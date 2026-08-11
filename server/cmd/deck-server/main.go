// Command deck-server é o servidor do App Deck: expõe a API JSON na rede
// local e abre programas no Windows quando o app Android pede.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/alexkobayashi/app-deck/server/internal/config"
	"github.com/alexkobayashi/app-deck/server/internal/httpapi"
	"github.com/alexkobayashi/app-deck/server/internal/launcher"
	"github.com/alexkobayashi/app-deck/server/internal/logging"
	"github.com/alexkobayashi/app-deck/server/internal/netinfo"
	"github.com/alexkobayashi/app-deck/server/internal/version"
)

// shutdownTimeout é quanto esperamos as requisições em voo terminarem.
const shutdownTimeout = 5 * time.Second

func main() {
	configureConsole()
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "erro:", err)
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
		showVersion = flag.Bool("version", false, "mostra a versão e sai")
	)
	flag.Parse()

	if *showVersion {
		fmt.Println(version.String())
		return nil
	}

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
	addr := cfg.Addr()
	if *bind != "" || *port != 0 {
		b, p := cfg.Bind, cfg.Port
		if *bind != "" {
			b = *bind
		}
		if *port != 0 {
			p = *port
		}
		addr = fmt.Sprintf("%s:%d", b, p)
	}

	api := &httpapi.API{
		Store:    store,
		Launcher: launcher.Exec{Log: log},
		Log:      log,
		Version:  version.Version,
	}
	srv := httpapi.NewHTTPServer(addr, api.Handler(), log)

	// O token nunca aparece no log. Para pareamento manual, o usuário lê
	// o config.json; a partir da fase da bandeja existe o QR.
	log.Info("app-deck-server iniciando",
		"version", version.Version,
		"commit", version.Commit,
		"addr", addr,
		"config", store.Path(),
		"log", logPath,
		"apps", len(cfg.Apps),
	)
	for _, ip := range netinfo.LocalIPv4s() {
		log.Info("endereço na rede local", "url", fmt.Sprintf("http://%s:%d", ip, cfg.Port))
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	serveErr := make(chan error, 1)
	go func() {
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serveErr <- err
			return
		}
		serveErr <- nil
	}()

	select {
	case err := <-serveErr:
		if err != nil {
			return fmt.Errorf("servidor HTTP: %w", err)
		}
		return nil
	case <-ctx.Done():
		log.Info("encerrando")
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("encerrar servidor: %w", err)
	}
	return <-serveErr
}
