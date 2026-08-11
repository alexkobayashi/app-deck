// Package launcher executa os programas dos atalhos.
//
// A abstração existe por dois motivos: permitir que os testes dos handlers
// HTTP rodem sem abrir programas de verdade, e isolar num único lugar as
// particularidades de criação de processo no Windows.
package launcher

import (
	"context"
	"errors"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
)

// Erros que o chamador pode querer distinguir.
var (
	ErrExecutableNotFound = errors.New("executável não encontrado")
	ErrNotExecutable      = errors.New("o caminho não aponta para um arquivo executável")
)

// Launcher abre o programa de um atalho.
//
// O ctx serve para logging e cancelamento futuro; ele deliberadamente NÃO
// é amarrado ao ciclo de vida do processo filho (exec.CommandContext
// mataria o programa quando a requisição HTTP terminasse).
type Launcher interface {
	Launch(ctx context.Context, path string, args ...string) error
}

// Exec é a implementação real, baseada em os/exec.
type Exec struct {
	Log *slog.Logger
}

func (e Exec) log() *slog.Logger {
	if e.Log != nil {
		return e.Log
	}
	return slog.Default()
}

// Launch inicia o programa e retorna imediatamente.
func (e Exec) Launch(_ context.Context, path string, args ...string) error {
	info, err := os.Stat(path)
	switch {
	case errors.Is(err, fs.ErrNotExist):
		return fmt.Errorf("%w: %s", ErrExecutableNotFound, path)
	case err != nil:
		return fmt.Errorf("verificar %s: %w", path, err)
	case info.IsDir():
		return fmt.Errorf("%w: %s", ErrNotExecutable, path)
	}

	cmd := exec.Command(path, args...)
	// Muitos programas do Windows procuram arquivos ao lado do próprio
	// executável e falham se o diretório de trabalho for outro.
	cmd.Dir = filepath.Dir(path)
	applyPlatform(cmd)

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("iniciar %s: %w", path, err)
	}

	// Start sem Wait deixaria o handle do processo aberto até o servidor
	// morrer. O Wait roda em background só para liberar esse recurso — a
	// requisição HTTP não espera o programa fechar.
	pid := cmd.Process.Pid
	go func() {
		if err := cmd.Wait(); err != nil {
			e.log().Debug("programa terminou com erro", "path", path, "pid", pid, "err", err)
		}
	}()

	e.log().Info("programa iniciado", "path", path, "pid", pid)
	return nil
}

// Fake registra as chamadas em vez de executar nada. Usado nos testes dos
// handlers HTTP.
type Fake struct {
	mu    sync.Mutex
	Err   error
	calls []Call
}

// Call é uma invocação registrada pelo Fake.
type Call struct {
	Path string
	Args []string
}

// Launch registra a chamada e devolve Fake.Err.
func (f *Fake) Launch(_ context.Context, path string, args ...string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.calls = append(f.calls, Call{Path: path, Args: append([]string(nil), args...)})
	return f.Err
}

// Calls devolve uma cópia das chamadas registradas.
func (f *Fake) Calls() []Call {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]Call(nil), f.calls...)
}
