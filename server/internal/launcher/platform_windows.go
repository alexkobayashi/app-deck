//go:build windows

package launcher

import (
	"os/exec"
	"syscall"
)

// Flags de CreateProcess (winbase.h). Declaradas localmente para o pacote
// não precisar de golang.org/x/sys.
const (
	// DETACHED_PROCESS: o filho não herda o console do servidor. Sem isso,
	// fechar o servidor pode derrubar os programas abertos, e um programa
	// de console herdaria a janela do servidor.
	detachedProcess = 0x00000008
	// CREATE_NEW_PROCESS_GROUP: o Ctrl+C do servidor não é propagado.
	createNewProcessGroup = 0x00000200
)

// applyPlatform desacopla o processo filho do servidor.
//
// Deliberadamente não usamos HideWindow: ele preenche wShowWindow com
// SW_HIDE no STARTUPINFO, e programas gráficos que honram esse campo
// abririam invisíveis — exatamente o oposto do que o usuário quer ao
// tocar num botão do deck.
func applyPlatform(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		CreationFlags: detachedProcess | createNewProcessGroup,
	}
}
