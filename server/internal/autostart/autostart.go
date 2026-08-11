// Package autostart liga e desliga a inicialização do servidor junto com o
// Windows.
//
// A fonte da verdade é o próprio registro do Windows, não o config.json:
// assim o estado exibido no menu da bandeja reflete o que o sistema vai
// realmente fazer, mesmo que o usuário mexa no registro por fora.
package autostart

import (
	"errors"
	"strings"
)

// ErrUnsupported é devolvido fora do Windows.
var ErrUnsupported = errors.New("iniciar com o sistema não é suportado nesta plataforma")

// Manager consulta e altera o estado do autostart.
type Manager interface {
	// Enabled informa se o autostart está ativo e apontando para este
	// executável.
	Enabled() (bool, error)
	Enable() error
	Disable() error
}

// quoteCommand monta a linha de comando gravada no registro.
//
// O caminho vem sempre entre aspas: sem elas, um exe em "C:\Program
// Files\..." faria o Windows tentar executar "C:\Program".
func quoteCommand(exe string, args []string) string {
	var sb strings.Builder
	sb.WriteString(`"` + exe + `"`)
	for _, a := range args {
		sb.WriteByte(' ')
		if strings.ContainsAny(a, " \t\"") {
			sb.WriteString(`"` + strings.ReplaceAll(a, `"`, `\"`) + `"`)
		} else {
			sb.WriteString(a)
		}
	}
	return sb.String()
}
