//go:build windows

package pairing

import (
	"fmt"
	"os/exec"
)

// openFile abre o arquivo no aplicativo padrão do Windows.
//
// rundll32 com FileProtocolHandler é equivalente a um duplo clique e não
// depende de um shell, o que importa quando o servidor roda como aplicativo
// de bandeja e não tem console.
func openFile(path string) error {
	cmd := exec.Command("rundll32", "url.dll,FileProtocolHandler", path)
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("abrir %s: %w", path, err)
	}
	go func() { _ = cmd.Wait() }()
	return nil
}
