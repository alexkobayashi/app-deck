//go:build windows

package main

import (
	"context"
	"fmt"
	"os/exec"

	"github.com/alexkobayashi/app-deck/server/internal/autostart"
	"github.com/alexkobayashi/app-deck/server/internal/tray"
)

// runUI bloqueia enquanto o servidor deve continuar rodando.
//
// No Windows o padrão é a bandeja do sistema. O --console mantém o
// comportamento antigo, útil para depurar com o log na tela.
func runUI(ctx context.Context, d appDeps) {
	if d.console {
		<-ctx.Done()
		d.onQuit()
		return
	}

	// Ctrl+C ou fim de sessão também precisam derrubar a bandeja.
	go func() {
		<-ctx.Done()
		tray.Quit()
	}()

	tray.Run(tray.Options{
		Version:    d.version,
		Icon:       tray.Icon(),
		Autostart:  autostart.New(autostartName, d.autostartArgs...),
		OnShowQR:   d.showQR,
		OnOpenLogs: d.openLogs,
		OnQuit:     d.onQuit,
		Log:        d.log,
	})
}

// openLogs abre a pasta de logs no Explorer.
func (d appDeps) openLogs() error {
	if d.logDir == "" {
		return fmt.Errorf("nenhum diretório de logs configurado")
	}
	cmd := exec.Command("explorer", d.logDir)
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("abrir %s: %w", d.logDir, err)
	}
	// O explorer.exe devolve status diferente de zero mesmo quando abre a
	// pasta com sucesso, então o resultado do Wait é ignorado de propósito —
	// ele existe só para liberar o handle do processo.
	go func() { _ = cmd.Wait() }()
	return nil
}
