//go:build windows

package tray

import (
	"errors"

	"fyne.io/systray"

	"github.com/alexkobayashi/app-deck/server/internal/autostart"
)

// Run mostra o ícone na bandeja e bloqueia até o usuário escolher "Sair" ou
// alguém chamar Quit.
//
// Deve ser chamada na goroutine principal: o laço de mensagens do Windows
// precisa rodar na thread que criou a janela.
func Run(opts Options) {
	systray.Run(func() { onReady(opts) }, func() {
		if opts.OnQuit != nil {
			opts.OnQuit()
		}
	})
}

// Quit encerra a bandeja, fazendo Run retornar.
func Quit() { systray.Quit() }

func onReady(opts Options) {
	log := opts.logger()

	if icon := opts.Icon; len(icon) > 0 {
		systray.SetIcon(icon)
	}
	systray.SetTitle("App Deck")
	systray.SetTooltip("App Deck — servidor " + opts.Version)

	title := systray.AddMenuItem("App Deck "+opts.Version, "")
	title.Disable()
	systray.AddSeparator()

	showQR := systray.AddMenuItem("Mostrar QR de pareamento",
		"Abre um QR code para o app Android escanear e se configurar sozinho")
	openLogs := systray.AddMenuItem("Abrir pasta de logs",
		"Abre no Explorer a pasta com os logs do servidor")

	autoStart := systray.AddMenuItemCheckbox("Iniciar com o Windows",
		"Registra o servidor para subir junto com a sessão do usuário", false)
	syncAutostart(opts, autoStart)

	systray.AddSeparator()
	quit := systray.AddMenuItem("Sair", "Encerra o servidor")

	go func() {
		for {
			select {
			case <-showQR.ClickedCh:
				if opts.OnShowQR != nil {
					if err := opts.OnShowQR(); err != nil {
						log.Error("não foi possível mostrar o QR de pareamento", "err", err)
					}
				}
			case <-openLogs.ClickedCh:
				if opts.OnOpenLogs != nil {
					if err := opts.OnOpenLogs(); err != nil {
						log.Error("não foi possível abrir a pasta de logs", "err", err)
					}
				}
			case <-autoStart.ClickedCh:
				toggleAutostart(opts, autoStart)
			case <-quit.ClickedCh:
				systray.Quit()
				return
			}
		}
	}()
}

// syncAutostart deixa o checkbox refletindo o registro do Windows.
func syncAutostart(opts Options, item *systray.MenuItem) {
	if opts.Autostart == nil {
		item.Disable()
		return
	}
	enabled, err := opts.Autostart.Enabled()
	if err != nil {
		if !errors.Is(err, autostart.ErrUnsupported) {
			opts.logger().Error("não foi possível consultar o autostart", "err", err)
		}
		item.Disable()
		return
	}
	if enabled {
		item.Check()
	} else {
		item.Uncheck()
	}
}

func toggleAutostart(opts Options, item *systray.MenuItem) {
	if opts.Autostart == nil {
		return
	}
	log := opts.logger()

	var err error
	if item.Checked() {
		err = opts.Autostart.Disable()
	} else {
		err = opts.Autostart.Enable()
	}
	if err != nil {
		log.Error("não foi possível alterar o autostart", "err", err)
	}

	// Reconsulta o registro em vez de assumir que a alteração pegou: se
	// falhou, o checkbox continua mostrando a verdade.
	syncAutostart(opts, item)
	if enabled, qerr := opts.Autostart.Enabled(); qerr == nil {
		log.Info("autostart alterado", "ativo", enabled)
	}
}
