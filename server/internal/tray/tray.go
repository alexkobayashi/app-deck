// Package tray põe o servidor na bandeja do sistema.
//
// Rodar como aplicativo de bandeja em vez de janela de console é um
// requisito de produto: o servidor fica ligado o dia inteiro, e uma janela
// de console aberta atrapalha e convida o usuário a fechá-la sem querer.
package tray

import (
	"embed"
	"log/slog"

	"github.com/alexkobayashi/app-deck/server/internal/autostart"
)

//go:embed assets/tray.ico
var assets embed.FS

// Icon devolve o ícone embutido no binário, no formato .ico exigido pela
// bandeja do Windows.
func Icon() []byte {
	data, err := assets.ReadFile("assets/tray.ico")
	if err != nil {
		// O arquivo é embutido em tempo de compilação: se faltasse, o build
		// teria falhado.
		return nil
	}
	return data
}

// Options configura o menu da bandeja.
//
// Os callbacks devolvem erro para que a bandeja possa registrar a falha no
// log — não há onde mostrar uma mensagem de erro para o usuário além dele.
type Options struct {
	Version    string
	Icon       []byte
	Autostart  autostart.Manager
	OnShowQR   func() error
	OnOpenLogs func() error
	OnQuit     func()
	Log        *slog.Logger
}

func (o Options) logger() *slog.Logger {
	if o.Log != nil {
		return o.Log
	}
	return slog.Default()
}
