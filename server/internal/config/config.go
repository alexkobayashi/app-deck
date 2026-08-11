// Package config carrega, valida e persiste a configuração do servidor.
//
// A configuração vive num único arquivo JSON. Como a API permite editar
// atalhos em runtime, toda escrita passa por writeFileAtomic (arquivo
// temporário + rename), de modo que um crash no meio da gravação nunca
// deixa um config.json truncado.
package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// CurrentVersion é o schema suportado por esta versão do servidor.
// Arquivos com version ausente ou menor são migrados na leitura.
const CurrentVersion = 2

// Padrões aplicados quando o campo vem vazio.
const (
	DefaultPort = 5050
	DefaultBind = "0.0.0.0"
)

// EnvConfigPath permite apontar o config.json por variável de ambiente,
// útil para rodar duas instâncias ou para testes manuais.
const EnvConfigPath = "APPDECK_CONFIG"

// FileName é o nome do arquivo de configuração.
const FileName = "config.json"

// App é um atalho: um programa que o app Android pode mandar abrir.
//
// O ID é gerado pelo servidor e é estável para sempre — o app Android
// associa o ícone customizado a ele. Renomear ou mover o executável não
// muda o ID.
type App struct {
	ID   string   `json:"id"`
	Name string   `json:"name"`
	Path string   `json:"path"`
	Args []string `json:"args,omitempty"`
}

// Config é o conteúdo do config.json.
type Config struct {
	Version int    `json:"version"`
	Token   string `json:"token"`
	Port    int    `json:"port"`
	Bind    string `json:"bind"`
	Apps    []App  `json:"apps"`
}

// Default devolve uma configuração vazia e válida, sem token (o Store
// gera um no primeiro Open).
func Default() Config {
	return Config{
		Version: CurrentVersion,
		Port:    DefaultPort,
		Bind:    DefaultBind,
		Apps:    []App{},
	}
}

// Addr é o endereço de escuta derivado de Bind e Port.
func (c Config) Addr() string {
	return fmt.Sprintf("%s:%d", c.Bind, c.Port)
}

// clone devolve uma cópia profunda: o slice de apps e os args de cada app
// são copiados, para que um Snapshot entregue ao chamador não possa ser
// mutado por baixo do Store.
func (c Config) clone() Config {
	out := c
	out.Apps = make([]App, len(c.Apps))
	for i, a := range c.Apps {
		out.Apps[i] = a
		if a.Args != nil {
			out.Apps[i].Args = append([]string(nil), a.Args...)
		}
	}
	return out
}

// indexOf devolve a posição do app com o ID dado, ou -1.
func (c Config) indexOf(id string) int {
	for i, a := range c.Apps {
		if a.ID == id {
			return i
		}
	}
	return -1
}

// ResolvePath decide onde fica o config.json, na ordem:
//
//  1. o caminho passado na flag --config;
//  2. a variável de ambiente APPDECK_CONFIG;
//  3. config.json ao lado do executável (modo portátil), se já existir
//     ou se o diretório for gravável;
//  4. %APPDATA%\AppDeck\config.json.
//
// A checagem de gravabilidade existe porque um .exe em "C:\Program Files"
// não consegue gravar no próprio diretório, e a escrita atômica falharia
// em toda edição de atalho feita pelo app.
func ResolvePath(flagPath string) (string, error) {
	if p := strings.TrimSpace(flagPath); p != "" {
		return filepath.Abs(p)
	}
	if p := strings.TrimSpace(os.Getenv(EnvConfigPath)); p != "" {
		return filepath.Abs(p)
	}

	if exe, err := os.Executable(); err == nil {
		dir := filepath.Dir(exe)
		portable := filepath.Join(dir, FileName)
		if _, err := os.Stat(portable); err == nil {
			return portable, nil
		}
		if dirWritable(dir) {
			return portable, nil
		}
	}

	base, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("localizar diretório de configuração do usuário: %w", err)
	}
	return filepath.Join(base, "AppDeck", FileName), nil
}

// dirWritable testa a gravabilidade criando e removendo um arquivo
// temporário — no Windows as permissões efetivas não são dedutíveis
// apenas do modo do diretório.
func dirWritable(dir string) bool {
	f, err := os.CreateTemp(dir, ".appdeck-probe-*")
	if err != nil {
		return false
	}
	name := f.Name()
	f.Close()
	os.Remove(name)
	return true
}
