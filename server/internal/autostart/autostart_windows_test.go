//go:build windows

package autostart

import (
	"strings"
	"testing"

	"golang.org/x/sys/windows/registry"
)

// testKeyPath fica fora da chave Run de verdade: um teste não deve conseguir
// registrar nada para iniciar com o Windows do usuário.
const testKeyPath = `Software\AppDeck\autostart-test`

func cleanupTestKey(t *testing.T) {
	t.Helper()
	t.Cleanup(func() {
		// Ignora os erros: a chave pode não existir, ou o pai pode ter
		// outras subchaves.
		_ = registry.DeleteKey(registry.CURRENT_USER, testKeyPath)
		_ = registry.DeleteKey(registry.CURRENT_USER, `Software\AppDeck`)
	})
}

func TestAutostartRoundtrip(t *testing.T) {
	cleanupTestKey(t)
	m := newWithKeyPath("AppDeckTest", testKeyPath)

	// A chave ainda não existe: não é erro, é só "desligado".
	enabled, err := m.Enabled()
	if err != nil {
		t.Fatalf("Enabled com a chave inexistente: %v", err)
	}
	if enabled {
		t.Fatal("Enabled = true antes de qualquer Enable")
	}

	if err := m.Enable(); err != nil {
		t.Fatalf("Enable: %v", err)
	}
	enabled, err = m.Enabled()
	if err != nil {
		t.Fatalf("Enabled depois de Enable: %v", err)
	}
	if !enabled {
		t.Fatal("Enabled = false depois de Enable")
	}

	// O valor gravado tem que estar entre aspas e apontar para este binário.
	key, err := registry.OpenKey(registry.CURRENT_USER, testKeyPath, registry.QUERY_VALUE)
	if err != nil {
		t.Fatalf("abrir chave de teste: %v", err)
	}
	got, _, err := key.GetStringValue("AppDeckTest")
	key.Close()
	if err != nil {
		t.Fatalf("ler valor: %v", err)
	}
	if !strings.HasPrefix(got, `"`) || !strings.HasSuffix(got, `"`) {
		t.Errorf("valor gravado não está entre aspas: %s", got)
	}
	if !strings.Contains(strings.ToLower(got), ".exe") {
		t.Errorf("valor gravado não aponta para um executável: %s", got)
	}

	if err := m.Disable(); err != nil {
		t.Fatalf("Disable: %v", err)
	}
	enabled, err = m.Enabled()
	if err != nil {
		t.Fatalf("Enabled depois de Disable: %v", err)
	}
	if enabled {
		t.Fatal("Enabled = true depois de Disable")
	}

	// Desligar duas vezes é idempotente.
	if err := m.Disable(); err != nil {
		t.Errorf("Disable duas vezes: %v", err)
	}
}

// Se a entrada no registro aponta para outro executável (o binário foi
// movido), o autostart está quebrado — e reportar "ligado" esconderia isso do
// usuário, que não teria como consertar pelo menu.
func TestEnabledIsFalseWhenValuePointsElsewhere(t *testing.T) {
	cleanupTestKey(t)
	m := newWithKeyPath("AppDeckTest", testKeyPath)

	key, _, err := registry.CreateKey(registry.CURRENT_USER, testKeyPath, registry.SET_VALUE)
	if err != nil {
		t.Fatalf("criar chave de teste: %v", err)
	}
	err = key.SetStringValue("AppDeckTest", `"C:\Outro\Lugar\app-deck-server.exe"`)
	key.Close()
	if err != nil {
		t.Fatalf("gravar valor: %v", err)
	}

	enabled, err := m.Enabled()
	if err != nil {
		t.Fatalf("Enabled: %v", err)
	}
	if enabled {
		t.Error("Enabled = true para uma entrada apontando para outro executável")
	}
}

// Os args viram parte da linha de comando gravada, senão o servidor iniciado
// pelo Windows usaria outra configuração.
func TestEnableKeepsArgs(t *testing.T) {
	cleanupTestKey(t)
	m := newWithKeyPath("AppDeckTest", testKeyPath, "--port", "5099")

	if err := m.Enable(); err != nil {
		t.Fatalf("Enable: %v", err)
	}

	key, err := registry.OpenKey(registry.CURRENT_USER, testKeyPath, registry.QUERY_VALUE)
	if err != nil {
		t.Fatal(err)
	}
	got, _, err := key.GetStringValue("AppDeckTest")
	key.Close()
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(got, "--port 5099") {
		t.Errorf("valor gravado = %s, esperava terminar com --port 5099", got)
	}

	// Enabled compara a linha inteira, então tem que continuar reconhecendo.
	enabled, err := m.Enabled()
	if err != nil || !enabled {
		t.Errorf("Enabled = (%v, %v) com args", enabled, err)
	}
}
