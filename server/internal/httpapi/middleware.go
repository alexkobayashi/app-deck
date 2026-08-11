package httpapi

import (
	"log/slog"
	"net"
	"net/http"
	"runtime/debug"
	"strings"
	"time"

	"github.com/alexkobayashi/app-deck/server/internal/token"
)

// auth exige o token no header Authorization.
//
// O protótipo aceitava o token na query string, o que o deixava gravado
// no histórico do navegador e nos logs de qualquer intermediário. Aqui o
// token só é aceito no header e a comparação é em tempo constante.
func (a *API) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		got, ok := bearerToken(r.Header.Get("Authorization"))
		if !ok || !token.Equal(got, a.Store.Token()) {
			w.Header().Set("WWW-Authenticate", `Bearer realm="app-deck"`)
			writeError(w, http.StatusUnauthorized, CodeUnauthorized,
				"não autorizado: envie o header Authorization: Bearer <token>")
			return
		}
		next(w, r)
	}
}

// bearerToken extrai o token de um header "Bearer <token>". O nome do
// esquema é case-insensitive conforme a RFC 7235.
func bearerToken(header string) (string, bool) {
	const prefix = "bearer "
	if len(header) <= len(prefix) || !strings.EqualFold(header[:len(prefix)], prefix) {
		return "", false
	}
	t := strings.TrimSpace(header[len(prefix):])
	if t == "" {
		return "", false
	}
	return t, true
}

// statusRecorder guarda o status e o tamanho da resposta para o log.
type statusRecorder struct {
	http.ResponseWriter
	status int
	bytes  int
}

func (s *statusRecorder) WriteHeader(status int) {
	s.status = status
	s.ResponseWriter.WriteHeader(status)
}

func (s *statusRecorder) Write(b []byte) (int, error) {
	n, err := s.ResponseWriter.Write(b)
	s.bytes += n
	return n, err
}

// Logging registra uma linha por requisição.
//
// Nunca registra o header Authorization, o corpo nem a query string —
// qualquer um dos três poderia vazar o token para o arquivo de log.
func Logging(log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}

			next.ServeHTTP(rec, r)

			log.Info("http",
				"method", r.Method,
				"path", r.URL.Path,
				"status", rec.status,
				"bytes", rec.bytes,
				"dur_ms", time.Since(start).Milliseconds(),
				"remote", remoteHost(r),
			)
		})
	}
}

// Recover impede que um panic num handler derrube o servidor inteiro.
func Recover(log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if rec := recover(); rec != nil {
					log.Error("panic no handler",
						"err", rec,
						"method", r.Method,
						"path", r.URL.Path,
						"stack", string(debug.Stack()),
					)
					writeError(w, http.StatusInternalServerError, CodeInternal, "erro interno")
				}
			}()
			next.ServeHTTP(w, r)
		})
	}
}

// remoteHost devolve só o IP do cliente, sem a porta efêmera.
func remoteHost(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}
