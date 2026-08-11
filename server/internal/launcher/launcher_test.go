package launcher

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

// harmlessProgram devolve um executável que termina imediatamente, para
// exercitar o caminho de sucesso sem abrir nada visível.
func harmlessProgram(t *testing.T) (string, []string) {
	t.Helper()
	if runtime.GOOS == "windows" {
		cmd := filepath.Join(os.Getenv("SystemRoot"), "System32", "cmd.exe")
		if _, err := os.Stat(cmd); err != nil {
			t.Skipf("cmd.exe não encontrado: %v", err)
		}
		return cmd, []string{"/c", "exit"}
	}
	for _, p := range []string{"/bin/true", "/usr/bin/true"} {
		if _, err := os.Stat(p); err == nil {
			return p, nil
		}
	}
	t.Skip("nenhum executável trivial disponível")
	return "", nil
}

func TestExecLaunchSuccess(t *testing.T) {
	path, args := harmlessProgram(t)
	l := Exec{Log: testLogger()}
	if err := l.Launch(context.Background(), path, args...); err != nil {
		t.Fatalf("Launch: %v", err)
	}
}

func TestExecLaunchMissingExecutable(t *testing.T) {
	l := Exec{Log: testLogger()}
	path := filepath.Join(t.TempDir(), "nao-existe.exe")

	err := l.Launch(context.Background(), path)
	if !errors.Is(err, ErrExecutableNotFound) {
		t.Fatalf("erro = %v, quero ErrExecutableNotFound", err)
	}
}

func TestExecLaunchDirectory(t *testing.T) {
	l := Exec{Log: testLogger()}
	err := l.Launch(context.Background(), t.TempDir())
	if !errors.Is(err, ErrNotExecutable) {
		t.Fatalf("erro = %v, quero ErrNotExecutable", err)
	}
}

// O launcher não deve amarrar o processo filho ao contexto da requisição:
// um ctx já cancelado ainda tem que abrir o programa, senão o programa
// morreria junto com a resposta HTTP.
func TestExecLaunchIgnoresCancelledContext(t *testing.T) {
	path, args := harmlessProgram(t)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	l := Exec{Log: testLogger()}
	if err := l.Launch(ctx, path, args...); err != nil {
		t.Fatalf("Launch com contexto cancelado: %v", err)
	}
}

func TestFakeRecordsCalls(t *testing.T) {
	f := &Fake{}
	if err := f.Launch(context.Background(), "C:\\a.exe", "--x"); err != nil {
		t.Fatal(err)
	}

	calls := f.Calls()
	if len(calls) != 1 {
		t.Fatalf("len(Calls) = %d, quero 1", len(calls))
	}
	if calls[0].Path != "C:\\a.exe" || len(calls[0].Args) != 1 || calls[0].Args[0] != "--x" {
		t.Errorf("chamada registrada = %+v", calls[0])
	}

	f.Err = errors.New("boom")
	if err := f.Launch(context.Background(), "C:\\b.exe"); err == nil {
		t.Error("esperava o erro configurado no Fake")
	}
}
