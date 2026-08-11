// Package httpapi expõe a API JSON consumida pelo app Android.
//
// Todas as rotas respondem JSON, inclusive os erros. Só /api/health é
// pública; as demais exigem Authorization: Bearer <token>.
package httpapi

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/alexkobayashi/app-deck/server/internal/config"
	"github.com/alexkobayashi/app-deck/server/internal/launcher"
)

// API reúne as dependências dos handlers.
type API struct {
	Store    *config.Store
	Launcher launcher.Launcher
	Log      *slog.Logger
	Version  string
}

func (a *API) logger() *slog.Logger {
	if a.Log != nil {
		return a.Log
	}
	return slog.Default()
}

// Handler monta o roteador com os middlewares aplicados.
//
// O roteamento usa os padrões method+path do net/http (Go 1.22+), o que
// dispensa qualquer roteador externo.
func (a *API) Handler() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /api/health", a.health)
	mux.HandleFunc("GET /api/apps", a.auth(a.listApps))
	mux.HandleFunc("POST /api/apps", a.auth(a.createApp))
	mux.HandleFunc("PUT /api/apps/{id}", a.auth(a.updateApp))
	mux.HandleFunc("DELETE /api/apps/{id}", a.auth(a.deleteApp))
	mux.HandleFunc("POST /api/apps/{id}/launch", a.auth(a.launchApp))

	// Catch-all para que uma rota ou método desconhecido também responda
	// JSON, em vez do texto puro que o ServeMux devolveria.
	mux.HandleFunc("/", a.notFound)

	log := a.logger()
	return Recover(log)(Logging(log)(mux))
}

// notFound responde 404 em JSON para qualquer coisa fora do contrato.
func (a *API) notFound(w http.ResponseWriter, r *http.Request) {
	writeError(w, http.StatusNotFound, CodeNotFound,
		"rota não encontrada: "+r.Method+" "+r.URL.Path)
}

// NewHTTPServer configura o servidor com timeouts explícitos — os padrões
// do net/http são "sem limite", o que deixaria uma conexão pendurada
// segurando recursos indefinidamente.
func NewHTTPServer(addr string, h http.Handler, log *slog.Logger) *http.Server {
	if log == nil {
		log = slog.Default()
	}
	return &http.Server{
		Addr:              addr,
		Handler:           h,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    1 << 16,
		ErrorLog:          slog.NewLogLogger(log.Handler(), slog.LevelError),
	}
}
