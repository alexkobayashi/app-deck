// Command deck-server é o servidor do App Deck: expõe a API JSON na rede
// local e abre programas no Windows quando o app Android pede.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/alexkobayashi/app-deck/server/internal/config"
	"github.com/alexkobayashi/app-deck/server/internal/httpapi"
	"github.com/alexkobayashi/app-deck/server/internal/launcher"
	"github.com/alexkobayashi/app-deck/server/internal/logging"
	"github.com/alexkobayashi/app-deck/server/internal/netinfo"
	"github.com/alexkobayashi/app-deck/server/internal/pairing"
	"github.com/alexkobayashi/app-deck/server/internal/version"
)

// shutdownTimeout é quanto esperamos as requisições em voo terminarem.
const shutdownTimeout = 5 * time.Second

// autostartName é o nome do valor gravado em HKCU\...\Run.
const autostartName = "AppDeck"

// appDeps é o que a interface de usuário (bandeja ou console) precisa saber.
type appDeps struct {
	log           *slog.Logger
	store         *config.Store
	version       string
	logDir        string
	dataDir       string
	port          int
	console       bool
	autostartArgs []string
}

// consoleMode registra se o servidor está rodando com o log na tela. É lido
// no tratamento de erro fatal, para decidir entre stderr e caixa de diálogo.
var consoleMode bool

func main() {
	configureConsole()
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "erro:", err)
		if !consoleMode {
			// Em modo bandeja não existe stderr: sem a caixa de diálogo, o
			// servidor simplesmente não apareceria e o usuário não teria
			// nenhuma pista do motivo.
			reportFatal(err.Error())
		}
		os.Exit(1)
	}
}

func run() error {
	var (
		configPath  = flag.String("config", "", "caminho do config.json (padrão: ao lado do executável ou %APPDATA%\\AppDeck)")
		port        = flag.Int("port", 0, "porta de escuta (sobrepõe o config.json)")
		bind        = flag.String("bind", "", "endereço de escuta (sobrepõe o config.json; 127.0.0.1 restringe ao próprio PC)")
		logLevel    = flag.String("log-level", "info", "nível de log: debug, info, warn ou error")
		logDir      = flag.String("log-dir", "", "diretório dos arquivos de log (padrão: %LOCALAPPDATA%\\AppDeck\\logs)")
		console     = flag.Bool("console", false, "roda no console em vez de na bandeja do sistema (útil para depurar)")
		showVersion = flag.Bool("version", false, "mostra a versão e sai")
	)
	flag.Parse()

	consoleMode = *console

	if *showVersion {
		fmt.Println(version.String())
		return nil
	}

	// Os flags explicitamente informados são preservados na entrada de
	// autostart, para que o servidor iniciado pelo Windows use a mesma
	// configuração que o usuário escolheu.
	explicit := map[string]string{}
	flag.Visit(func(f *flag.Flag) { explicit[f.Name] = f.Value.String() })

	level, err := logging.ParseLevel(*logLevel)
	if err != nil {
		return err
	}

	dir := *logDir
	if dir == "" {
		if d, err := logging.DefaultLogDir(); err == nil {
			dir = d
		}
	}
	log, closeLog, logPath := logging.Setup(level, dir, true)
	defer func() { _ = closeLog() }()

	cfgPath, err := config.ResolvePath(*configPath)
	if err != nil {
		return err
	}

	store, warnings, err := config.Open(cfgPath, log)
	if err != nil {
		return err
	}
	// Avisos nunca impedem o servidor de subir: um atalho apontando para
	// um programa desinstalado deve gerar uma linha clara no log, não uma
	// falha de startup.
	for _, w := range warnings {
		log.Warn("configuração", "aviso", w.String())
	}

	cfg := store.Snapshot()
	effectiveBind, effectivePort := cfg.Bind, cfg.Port
	if *bind != "" {
		effectiveBind = *bind
	}
	if *port != 0 {
		effectivePort = *port
	}
	addr := fmt.Sprintf("%s:%d", effectiveBind, effectivePort)

	dataDir, err := pairing.DefaultDir()
	if err != nil {
		log.Warn("não foi possível localizar o diretório de dados; o QR de pareamento ficará ao lado do config", "err", err)
		dataDir = filepath.Dir(store.Path())
	}

	api := &httpapi.API{
		Store:    store,
		Launcher: launcher.Exec{Log: log},
		Log:      log,
		Version:  version.Version,
	}
	srv := httpapi.NewHTTPServer(addr, api.Handler(), log)

	// O token nunca aparece no log. O pareamento é feito pelo QR da bandeja
	// ou lendo o config.json.
	log.Info("app-deck-server iniciando",
		"version", version.Version,
		"commit", version.Commit,
		"addr", addr,
		"config", store.Path(),
		"log", logPath,
		"apps", len(cfg.Apps),
	)
	for _, ip := range netinfo.LocalIPv4s() {
		log.Info("endereço na rede local", "url", fmt.Sprintf("http://%s:%d", ip, effectivePort))
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	serveErr := make(chan error, 1)
	go func() {
		err := srv.ListenAndServe()
		if errors.Is(err, http.ErrServerClosed) {
			err = nil
		}
		serveErr <- err
	}()

	deps := appDeps{
		log:           log,
		store:         store,
		version:       version.Version,
		logDir:        dir,
		dataDir:       dataDir,
		port:          effectivePort,
		console:       *console,
		autostartArgs: autostartArgs(explicit, cfgPath),
	}

	// Bloqueia até o usuário sair pela bandeja, um sinal chegar, ou o
	// servidor HTTP falhar (porta em uso, por exemplo).
	uiDone := make(chan struct{})
	go func() {
		runUI(ctx, deps)
		close(uiDone)
	}()

	select {
	case err := <-serveErr:
		if err != nil {
			return fmt.Errorf("servidor HTTP: %w", err)
		}
		return nil
	case <-uiDone:
		log.Info("encerrando")
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("encerrar servidor: %w", err)
	}
	return <-serveErr
}

// autostartArgs reconstrói os flags relevantes para a linha de comando
// gravada no registro. O --config usa o caminho já resolvido, porque o
// diretório de trabalho na inicialização do Windows não é o mesmo de agora.
func autostartArgs(explicit map[string]string, resolvedConfig string) []string {
	var args []string
	if _, ok := explicit["config"]; ok {
		args = append(args, "--config", resolvedConfig)
	}
	for _, name := range []string{"port", "bind", "log-level", "log-dir"} {
		if v, ok := explicit[name]; ok {
			args = append(args, "--"+name, v)
		}
	}
	return args
}

// showQR gera o QR de pareamento e o abre no visualizador padrão.
func (d appDeps) showQR() error {
	p := pairing.Build(netinfo.PreferredIPv4(), d.port, d.store.Token())
	path, err := pairing.Show(p, d.dataDir, pairing.DefaultSize)
	if err != nil {
		if path == "" {
			return err
		}
		// O QR existe, só não abriu o visualizador: o usuário ainda pode
		// abrir o arquivo manualmente.
		d.log.Warn("QR gerado, mas o visualizador não abriu", "path", path, "err", err)
		return nil
	}
	d.log.Info("QR de pareamento gerado", "path", path, "url", p.URL())
	return nil
}

// onQuit roda no encerramento, antes de o processo sair.
func (d appDeps) onQuit() {
	// O PNG do QR contém o token em claro: não pode ficar no disco depois
	// que o servidor sai.
	if err := pairing.Remove(d.dataDir); err != nil {
		d.log.Warn("não foi possível remover o QR de pareamento", "err", err)
	}
}
