// Package version guarda os metadados de build do servidor.
//
// Os valores são injetados no link time pelo workflow de release:
//
//	go build -ldflags "-X github.com/alexkobayashi/app-deck/server/internal/version.Version=v1.2.3"
package version

import "fmt"

// Valores sobrescritos via -ldflags. Um binário compilado localmente
// reporta "dev".
var (
	Version = "dev"
	Commit  = "none"
	Date    = "unknown"
)

// String devolve uma linha legível com versão, commit e data de build.
func String() string {
	return fmt.Sprintf("app-deck-server %s (commit %s, build %s)", Version, Commit, Date)
}
