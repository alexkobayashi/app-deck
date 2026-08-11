//go:build windows

package autostart

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"golang.org/x/sys/windows/registry"
)

// RunKeyPath é a chave de inicialização do usuário atual. HKCU não exige
// privilégio de administrador — é justamente por isso que ela é usada em vez
// de HKLM ou da pasta Inicializar.
const RunKeyPath = `Software\Microsoft\Windows\CurrentVersion\Run`

type windowsManager struct {
	valueName string
	keyPath   string
	args      []string
}

// New devolve o gerenciador de autostart. Os args são preservados na linha de
// comando gravada, para que um servidor iniciado com --config continue
// usando o mesmo config quando o Windows o iniciar sozinho.
func New(appName string, args ...string) Manager {
	return newWithKeyPath(appName, RunKeyPath, args...)
}

// newWithKeyPath permite aos testes escreverem numa chave própria em vez de
// mexer na chave Run de verdade do usuário.
func newWithKeyPath(appName, keyPath string, args ...string) *windowsManager {
	return &windowsManager{valueName: appName, keyPath: keyPath, args: args}
}

// command é a linha de comando esperada no registro para este executável.
func (m *windowsManager) command() (string, error) {
	exe, err := os.Executable()
	if err != nil {
		return "", fmt.Errorf("localizar o próprio executável: %w", err)
	}
	abs, err := filepath.Abs(exe)
	if err != nil {
		return "", fmt.Errorf("resolver o caminho do executável: %w", err)
	}
	return quoteCommand(abs, m.args), nil
}

func (m *windowsManager) Enabled() (bool, error) {
	key, err := registry.OpenKey(registry.CURRENT_USER, m.keyPath, registry.QUERY_VALUE)
	if err != nil {
		if errors.Is(err, registry.ErrNotExist) {
			return false, nil
		}
		return false, fmt.Errorf("abrir %s: %w", m.keyPath, err)
	}
	defer key.Close()

	got, _, err := key.GetStringValue(m.valueName)
	if errors.Is(err, registry.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("ler o valor %s: %w", m.valueName, err)
	}

	want, err := m.command()
	if err != nil {
		return false, err
	}
	// Comparar com o executável atual, e não apenas checar a existência do
	// valor, evita reportar "ligado" quando a entrada aponta para um .exe
	// que foi movido ou apagado — nesse caso o autostart está quebrado, e o
	// menu deve mostrar desligado para o usuário poder consertar num clique.
	return strings.EqualFold(strings.TrimSpace(got), want), nil
}

func (m *windowsManager) Enable() error {
	cmd, err := m.command()
	if err != nil {
		return err
	}
	key, _, err := registry.CreateKey(registry.CURRENT_USER, m.keyPath, registry.SET_VALUE)
	if err != nil {
		return fmt.Errorf("abrir %s para escrita: %w", m.keyPath, err)
	}
	defer key.Close()

	if err := key.SetStringValue(m.valueName, cmd); err != nil {
		return fmt.Errorf("gravar o valor %s: %w", m.valueName, err)
	}
	return nil
}

func (m *windowsManager) Disable() error {
	key, err := registry.OpenKey(registry.CURRENT_USER, m.keyPath, registry.SET_VALUE)
	if err != nil {
		if errors.Is(err, registry.ErrNotExist) {
			return nil
		}
		return fmt.Errorf("abrir %s para escrita: %w", m.keyPath, err)
	}
	defer key.Close()

	if err := key.DeleteValue(m.valueName); err != nil && !errors.Is(err, registry.ErrNotExist) {
		return fmt.Errorf("remover o valor %s: %w", m.valueName, err)
	}
	return nil
}
