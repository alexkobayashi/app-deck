package config

import (
	"errors"
	"fmt"
	"io/fs"
	"strings"
)

// ErrInvalid marca uma configuração ou requisição inválida. Handlers HTTP
// traduzem para 400.
var ErrInvalid = errors.New("configuração inválida")

// Warning é um problema que merece log mas não impede o servidor de subir.
//
// O requisito é explícito: um atalho apontando para um executável que não
// existe mais deve gerar aviso claro, nunca falha silenciosa nem travamento.
type Warning struct {
	AppID string
	Field string
	Msg   string
}

func (w Warning) String() string {
	if w.AppID == "" {
		return fmt.Sprintf("%s: %s", w.Field, w.Msg)
	}
	return fmt.Sprintf("app %s (%s): %s", w.AppID, w.Field, w.Msg)
}

// StatFunc é injetável para que a validação de caminhos possa ser testada
// em qualquer sistema operacional, sem depender de caminhos do Windows.
type StatFunc func(string) (fs.FileInfo, error)

// normalize preenche os campos ausentes e gera os IDs que faltam.
// Devolve os avisos gerados e true se algo mudou (o chamador deve regravar).
func (c *Config) normalize() ([]Warning, bool, error) {
	var warns []Warning
	changed := false

	if c.Version != CurrentVersion {
		c.Version = CurrentVersion
		changed = true
	}
	if c.Port == 0 {
		c.Port = DefaultPort
		changed = true
	}
	if strings.TrimSpace(c.Bind) == "" {
		c.Bind = DefaultBind
		changed = true
	}
	if c.Apps == nil {
		c.Apps = []App{}
		changed = true
	}

	seen := make(map[string]bool, len(c.Apps))
	for i := range c.Apps {
		app := &c.Apps[i]

		if n := strings.TrimSpace(app.Name); n != app.Name {
			app.Name = n
			changed = true
		}
		// Limpar aqui conserta configs que já estão em disco: normalize
		// devolve changed=true e o Open regrava o arquivo, então o caractere
		// invisível desaparece de vez em vez de voltar a incomodar.
		if p, removed := cleanPath(app.Path); p != app.Path {
			if len(removed) > 0 {
				warns = append(warns, invisibleCharWarning(app.ID, app.Path, removed))
			}
			app.Path = p
			changed = true
		}

		// ID ausente ou duplicado: gera um novo. Duplicata só acontece se
		// alguém editou o arquivo à mão copiando um bloco de app.
		if app.ID == "" || seen[app.ID] {
			id, err := NewID()
			if err != nil {
				return warns, changed, err
			}
			if app.ID != "" {
				warns = append(warns, Warning{
					AppID: app.ID,
					Field: "id",
					Msg:   "id duplicado no config.json; um novo id foi gerado (o ícone customizado no app será perdido para este atalho)",
				})
			}
			app.ID = id
			changed = true
		}
		seen[app.ID] = true
	}

	return warns, changed, nil
}

// validate recusa apenas o que impede o servidor de funcionar.
func (c Config) validate() error {
	if c.Port < 1 || c.Port > 65535 {
		return fmt.Errorf("%w: port %d fora do intervalo 1-65535", ErrInvalid, c.Port)
	}
	if c.Token == "" {
		return fmt.Errorf("%w: token vazio", ErrInvalid)
	}
	for i, a := range c.Apps {
		if a.ID == "" {
			return fmt.Errorf("%w: apps[%d] sem id", ErrInvalid, i)
		}
		if a.Name == "" {
			return fmt.Errorf("%w: apps[%d] (%s) sem name", ErrInvalid, i, a.ID)
		}
		if a.Path == "" {
			return fmt.Errorf("%w: apps[%d] (%s) sem path", ErrInvalid, i, a.ID)
		}
	}
	return nil
}

// CheckPaths verifica se o executável de cada atalho existe. O resultado é
// informativo: caminhos quebrados viram avisos, não erros.
func CheckPaths(c Config, stat StatFunc) []Warning {
	var warns []Warning
	for _, a := range c.Apps {
		info, err := stat(a.Path)
		switch {
		case errors.Is(err, fs.ErrNotExist):
			warns = append(warns, Warning{
				AppID: a.ID,
				Field: "path",
				Msg:   fmt.Sprintf("executável não encontrado: %s", a.Path),
			})
		case err != nil:
			warns = append(warns, Warning{
				AppID: a.ID,
				Field: "path",
				Msg:   fmt.Sprintf("não foi possível verificar %s: %v", a.Path, err),
			})
		case info.IsDir():
			warns = append(warns, Warning{
				AppID: a.ID,
				Field: "path",
				Msg:   fmt.Sprintf("path aponta para um diretório, não um executável: %s", a.Path),
			})
		}
	}
	return warns
}
