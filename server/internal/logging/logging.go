// Package logging monta o logger estruturado do servidor.
//
// O log em arquivo não é um luxo: quando o servidor roda como aplicativo
// de bandeja (compilado com -H=windowsgui) não existe stdout, e o arquivo
// passa a ser a única forma de investigar um problema.
package logging

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

// maxLogBytes é o tamanho a partir do qual o arquivo é rotacionado.
const maxLogBytes = 5 << 20

// FileName é o nome do arquivo de log dentro do diretório de logs.
const FileName = "app-deck-server.log"

// Setup devolve o logger, uma função de fechamento e o caminho do arquivo
// de log em uso.
//
// Se logDir for vazio, o log vai só para o console. Se o arquivo não puder
// ser aberto, o servidor não falha: cai para o console e reporta o erro
// como aviso pelo próprio logger devolvido.
func Setup(level slog.Level, logDir string, alsoConsole bool) (*slog.Logger, func() error, string) {
	opts := &slog.HandlerOptions{Level: level}

	var handlers []slog.Handler
	closers := []func() error{}
	logPath := ""

	if alsoConsole {
		handlers = append(handlers, slog.NewTextHandler(os.Stderr, opts))
	}

	var setupErr error
	if logDir != "" {
		if err := os.MkdirAll(logDir, 0o755); err != nil {
			setupErr = fmt.Errorf("criar diretório de logs %s: %w", logDir, err)
		} else {
			path := filepath.Join(logDir, FileName)
			f, err := newRotatingFile(path, maxLogBytes)
			if err != nil {
				setupErr = fmt.Errorf("abrir arquivo de log %s: %w", path, err)
			} else {
				handlers = append(handlers, slog.NewJSONHandler(f, opts))
				closers = append(closers, f.Close)
				logPath = path
			}
		}
	}

	if len(handlers) == 0 {
		handlers = append(handlers, slog.NewTextHandler(os.Stderr, opts))
	}

	log := slog.New(&multiHandler{handlers: handlers})
	if setupErr != nil {
		log.Warn("log em arquivo desabilitado", "err", setupErr)
	}

	closeAll := func() error {
		var firstErr error
		for _, c := range closers {
			if err := c(); err != nil && firstErr == nil {
				firstErr = err
			}
		}
		return firstErr
	}
	return log, closeAll, logPath
}

// DefaultLogDir é %LOCALAPPDATA%\AppDeck\logs no Windows.
func DefaultLogDir() (string, error) {
	base, err := os.UserCacheDir()
	if err != nil {
		return "", fmt.Errorf("localizar diretório de cache do usuário: %w", err)
	}
	return filepath.Join(base, "AppDeck", "logs"), nil
}

// ParseLevel converte "debug", "info", "warn" ou "error" em slog.Level.
func ParseLevel(s string) (slog.Level, error) {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "debug":
		return slog.LevelDebug, nil
	case "", "info":
		return slog.LevelInfo, nil
	case "warn", "warning":
		return slog.LevelWarn, nil
	case "error":
		return slog.LevelError, nil
	default:
		return slog.LevelInfo, fmt.Errorf("nível de log inválido: %q (use debug, info, warn ou error)", s)
	}
}

// multiHandler envia cada registro para todos os handlers configurados.
type multiHandler struct {
	handlers []slog.Handler
}

func (m *multiHandler) Enabled(ctx context.Context, level slog.Level) bool {
	for _, h := range m.handlers {
		if h.Enabled(ctx, level) {
			return true
		}
	}
	return false
}

func (m *multiHandler) Handle(ctx context.Context, r slog.Record) error {
	var firstErr error
	for _, h := range m.handlers {
		if !h.Enabled(ctx, r.Level) {
			continue
		}
		// Cada handler recebe sua própria cópia: um Record não pode ser
		// consumido duas vezes.
		if err := h.Handle(ctx, r.Clone()); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}

func (m *multiHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	next := make([]slog.Handler, len(m.handlers))
	for i, h := range m.handlers {
		next[i] = h.WithAttrs(attrs)
	}
	return &multiHandler{handlers: next}
}

func (m *multiHandler) WithGroup(name string) slog.Handler {
	next := make([]slog.Handler, len(m.handlers))
	for i, h := range m.handlers {
		next[i] = h.WithGroup(name)
	}
	return &multiHandler{handlers: next}
}

// rotatingFile é uma rotação por tamanho deliberadamente simples: ao
// passar de max, o arquivo atual vira ".1" e um novo é aberto. Guarda
// apenas uma geração — o suficiente para diagnosticar um problema recente
// sem depender de biblioteca externa.
type rotatingFile struct {
	mu   sync.Mutex
	path string
	max  int64
	f    *os.File
	size int64
}

var _ io.WriteCloser = (*rotatingFile)(nil)

func newRotatingFile(path string, max int64) (*rotatingFile, error) {
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
	if err != nil {
		return nil, err
	}
	info, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, err
	}
	return &rotatingFile{path: path, max: max, f: f, size: info.Size()}, nil
}

func (r *rotatingFile) Write(p []byte) (int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.size+int64(len(p)) > r.max {
		if err := r.rotate(); err != nil {
			return 0, err
		}
	}
	n, err := r.f.Write(p)
	r.size += int64(n)
	return n, err
}

func (r *rotatingFile) rotate() error {
	if err := r.f.Close(); err != nil {
		return err
	}
	// No Windows, Rename por cima de um arquivo existente funciona, mas o
	// destino é removido antes por clareza.
	old := r.path + ".1"
	os.Remove(old)
	if err := os.Rename(r.path, old); err != nil {
		// Se a rotação falhar (arquivo em uso, por exemplo), continuar
		// escrevendo no mesmo arquivo é melhor que perder o log.
		f, openErr := os.OpenFile(r.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
		if openErr != nil {
			return openErr
		}
		r.f = f
		return nil
	}
	f, err := os.OpenFile(r.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
	if err != nil {
		return err
	}
	r.f = f
	r.size = 0
	return nil
}

func (r *rotatingFile) Close() error {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.f.Close()
}
