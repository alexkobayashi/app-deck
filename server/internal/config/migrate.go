package config

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

// BackupSuffix é acrescentado ao config.json antes de regravá-lo no
// formato novo, para que o arquivo original do protótipo nunca se perca.
const BackupSuffix = ".v1.bak"

// legacyApp é o formato de atalho do protótipo (reference/main.go): sem
// id e com o emoji junto. O emoji é descartado — a v2 é explícita em que
// o servidor não sabe nada sobre ícones, quem cuida disso é o app Android.
type legacyApp struct {
	Name string `json:"name"`
	Icon string `json:"icon"`
	Path string `json:"path"`
}

// legacyConfig é o config.json do protótipo, com "porta" como string.
type legacyConfig struct {
	Token string      `json:"token"`
	Porta string      `json:"porta"`
	Apps  []legacyApp `json:"apps"`
}

// versionProbe lê só o suficiente para decidir qual decodificador usar.
type versionProbe struct {
	Version *int `json:"version"`
}

// isLegacy considera legado todo arquivo sem "version" ou com version < 2.
// É a regra mais robusta: cobre o "porta" string, apps sem id e qualquer
// arquivo herdado do protótipo.
func isLegacy(raw []byte) (bool, error) {
	var probe versionProbe
	if err := json.Unmarshal(raw, &probe); err != nil {
		return false, fmt.Errorf("config.json inválido: %w", err)
	}
	return probe.Version == nil || *probe.Version < CurrentVersion, nil
}

// migrate converte o formato do protótipo para o schema atual.
//
// Conversões: "porta" string → port int, cada app ganha um id de
// crypto/rand, o campo icon é descartado. O token é preservado para não
// quebrar um pareamento existente; se for fraco, o chamador avisa.
func migrate(raw []byte) (Config, []Warning, error) {
	var old legacyConfig
	if err := json.Unmarshal(raw, &old); err != nil {
		return Config{}, nil, fmt.Errorf("config.json no formato antigo é inválido: %w", err)
	}

	cfg := Default()
	cfg.Token = strings.TrimSpace(old.Token)

	var warns []Warning
	if p := strings.TrimSpace(old.Porta); p != "" {
		port, err := strconv.Atoi(p)
		if err != nil || port < 1 || port > 65535 {
			warns = append(warns, Warning{
				Field: "porta",
				Msg:   fmt.Sprintf("valor %q inválido; usando a porta padrão %d", p, DefaultPort),
			})
		} else {
			cfg.Port = port
		}
	}

	cfg.Apps = make([]App, 0, len(old.Apps))
	for _, a := range old.Apps {
		id, err := NewID()
		if err != nil {
			return Config{}, warns, err
		}
		cfg.Apps = append(cfg.Apps, App{
			ID:   id,
			Name: strings.TrimSpace(a.Name),
			Path: strings.TrimSpace(a.Path),
		})
	}

	return cfg, warns, nil
}
