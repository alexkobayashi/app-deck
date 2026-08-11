package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestWriteFileAtomicReplacesContent(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "arquivo.json")

	if err := os.WriteFile(path, []byte("antigo"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := writeFileAtomic(path, []byte("novo"), 0o600); err != nil {
		t.Fatalf("writeFileAtomic: %v", err)
	}

	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "novo" {
		t.Errorf("conteúdo = %q, quero %q", got, "novo")
	}
	assertNoTempLeftovers(t, dir)
}

func TestWriteFileAtomicCreatesFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "novo.json")

	if err := writeFileAtomic(path, []byte("conteúdo"), 0o600); err != nil {
		t.Fatalf("writeFileAtomic: %v", err)
	}
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("arquivo não foi criado: %v", err)
	}
	assertNoTempLeftovers(t, dir)
}

func TestWriteFileAtomicMissingDirFails(t *testing.T) {
	path := filepath.Join(t.TempDir(), "nao-existe", "arquivo.json")
	if err := writeFileAtomic(path, []byte("x"), 0o600); err == nil {
		t.Fatal("esperava erro ao gravar em diretório inexistente")
	}
}

// O destino ser um diretório faz o rename final falhar. O ponto do teste é
// garantir que uma falha nesse último passo não deixa temporário para trás
// nem destrói o que já existia.
func TestWriteFileAtomicFailureKeepsTargetAndCleansUp(t *testing.T) {
	dir := t.TempDir()
	target := filepath.Join(dir, "alvo")
	if err := os.Mkdir(target, 0o755); err != nil {
		t.Fatal(err)
	}

	if err := writeFileAtomic(target, []byte("x"), 0o600); err == nil {
		t.Fatal("esperava erro ao substituir um diretório por um arquivo")
	}

	info, err := os.Stat(target)
	if err != nil {
		t.Fatalf("o alvo original desapareceu: %v", err)
	}
	if !info.IsDir() {
		t.Error("o alvo original foi substituído")
	}
	assertNoTempLeftovers(t, dir)
}

func assertNoTempLeftovers(t *testing.T, dir string) {
	t.Helper()
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range entries {
		if strings.HasPrefix(e.Name(), ".appdeck-") {
			t.Errorf("arquivo temporário deixado para trás: %s", e.Name())
		}
	}
}
