package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/alexkobayashi/app-deck/server/internal/token"
)

// ErrNotFound é devolvido quando o id do atalho não existe. Handlers HTTP
// traduzem para 404.
var ErrNotFound = errors.New("atalho não encontrado")

// Store é o dono da configuração: única fonte de verdade em memória e
// responsável por persistir cada alteração.
//
// Invariante: memória e disco nunca divergem. Toda mutação é aplicada a
// uma cópia, gravada em disco e só então comitada em memória — se a
// gravação falhar, o estado em memória continua sendo o que está no
// arquivo.
type Store struct {
	mu   sync.RWMutex
	path string
	cfg  Config
	log  *slog.Logger
}

// AppUpdate descreve uma edição parcial de atalho. Campos nil ficam como
// estão, o que permite ao app Android mandar só o que mudou sem correr o
// risco de apagar o resto.
type AppUpdate struct {
	Name *string
	Path *string
	Args *[]string
}

// Open carrega o config.json, migrando e normalizando o que for preciso.
//
// Se o arquivo não existir, cria um com token novo. Se estiver no formato
// do protótipo, migra e guarda um backup .v1.bak. Devolve os avisos
// acumulados (token fraco, executável inexistente, id duplicado) para que
// o chamador os registre no log — nenhum deles impede o servidor de subir.
func Open(path string, log *slog.Logger) (*Store, []Warning, error) {
	if log == nil {
		log = slog.Default()
	}
	s := &Store{path: path, log: log}

	raw, err := os.ReadFile(path)
	if errors.Is(err, fs.ErrNotExist) {
		return s.create()
	}
	if err != nil {
		return nil, nil, fmt.Errorf("ler %s: %w", path, err)
	}

	var warns []Warning
	changed := false

	legacy, err := isLegacy(raw)
	if err != nil {
		return nil, nil, err
	}

	var cfg Config
	if legacy {
		migrated, mw, err := migrate(raw)
		if err != nil {
			return nil, nil, err
		}
		// O backup vem antes de qualquer regravação: se algo der errado
		// depois, o arquivo do protótipo ainda existe.
		backup := path + BackupSuffix
		if err := os.WriteFile(backup, raw, 0o600); err != nil {
			return nil, nil, fmt.Errorf("gravar backup %s: %w", backup, err)
		}
		cfg = migrated
		warns = append(warns, mw...)
		changed = true
		log.Info("config migrado do formato do protótipo (v1) para v2",
			"apps", len(cfg.Apps), "backup", backup)
	} else if err := json.Unmarshal(raw, &cfg); err != nil {
		return nil, nil, fmt.Errorf("config.json inválido: %w", err)
	}

	nw, normChanged, err := cfg.normalize()
	if err != nil {
		return nil, nil, err
	}
	warns = append(warns, nw...)
	changed = changed || normChanged

	if strings.TrimSpace(cfg.Token) == "" {
		newToken, err := token.Generate()
		if err != nil {
			return nil, nil, err
		}
		cfg.Token = newToken
		changed = true
		warns = append(warns, Warning{
			Field: "token",
			Msg:   "nenhum token configurado; um token aleatório foi gerado — use o QR de pareamento ou leia o config.json para configurar o app",
		})
	} else if token.Weak(cfg.Token) {
		warns = append(warns, Warning{
			Field: "token",
			Msg:   "o token configurado é curto e fácil de adivinhar; apague o campo \"token\" do config.json para o servidor gerar um forte no próximo start",
		})
	}

	if err := cfg.validate(); err != nil {
		return nil, warns, err
	}

	if changed {
		if err := s.persist(cfg); err != nil {
			return nil, warns, err
		}
	}
	s.cfg = cfg

	warns = append(warns, CheckPaths(cfg, os.Stat)...)
	return s, warns, nil
}

// create grava um config.json novo, com token forte e sem atalhos.
func (s *Store) create() (*Store, []Warning, error) {
	cfg := Default()
	newToken, err := token.Generate()
	if err != nil {
		return nil, nil, err
	}
	cfg.Token = newToken

	dir := filepath.Dir(s.path)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, nil, fmt.Errorf("criar diretório %s: %w", dir, err)
	}
	if err := s.persist(cfg); err != nil {
		return nil, nil, err
	}
	s.cfg = cfg

	s.log.Info("config.json criado", "path", s.path)
	return s, []Warning{{
		Field: "config",
		Msg: fmt.Sprintf("config.json não existia e foi criado em %s com um token novo; "+
			"nenhum atalho configurado ainda — adicione pelo app ou edite o arquivo", s.path),
	}}, nil
}

// persist serializa e grava atomicamente, sem tocar no estado em memória.
func (s *Store) persist(cfg Config) error {
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return fmt.Errorf("serializar configuração: %w", err)
	}
	data = append(data, '\n')
	if err := writeFileAtomic(s.path, data, 0o600); err != nil {
		return fmt.Errorf("salvar %s: %w", s.path, err)
	}
	return nil
}

// Path é o caminho do arquivo de configuração em uso.
func (s *Store) Path() string { return s.path }

// Snapshot devolve uma cópia profunda da configuração atual.
func (s *Store) Snapshot() Config {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cfg.clone()
}

// Token devolve o token de autenticação em vigor.
func (s *Store) Token() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cfg.Token
}

// Apps devolve os atalhos na ordem em que estão no arquivo — que é a
// ordem em que o deck os exibe.
func (s *Store) Apps() []App {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cfg.clone().Apps
}

// AppByID busca um atalho pelo id.
func (s *Store) AppByID(id string) (App, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	i := s.cfg.indexOf(id)
	if i < 0 {
		return App{}, false
	}
	return s.cfg.clone().Apps[i], true
}

// AddApp cria um atalho novo no fim da lista.
func (s *Store) AddApp(name, path string, args []string) (App, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	name = strings.TrimSpace(name)
	path, removed := cleanPath(path)
	if name == "" {
		return App{}, fmt.Errorf("%w: name é obrigatório", ErrInvalid)
	}
	if path == "" {
		return App{}, fmt.Errorf("%w: path é obrigatório", ErrInvalid)
	}
	if len(removed) > 0 {
		s.log.Warn("caractere invisível removido do path do novo atalho",
			"name", name, "removidos", describeRunes(removed))
	}

	id, err := NewID()
	if err != nil {
		return App{}, err
	}
	app := App{ID: id, Name: name, Path: path}
	if len(args) > 0 {
		app.Args = append([]string(nil), args...)
	}

	next := s.cfg.clone()
	next.Apps = append(next.Apps, app)
	if err := s.persist(next); err != nil {
		return App{}, err
	}
	s.cfg = next

	s.log.Info("atalho adicionado", "id", app.ID, "name", app.Name)
	return app, nil
}

// UpdateApp altera os campos informados de um atalho. O id nunca muda.
func (s *Store) UpdateApp(id string, upd AppUpdate) (App, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	i := s.cfg.indexOf(id)
	if i < 0 {
		return App{}, fmt.Errorf("%w: %s", ErrNotFound, id)
	}

	next := s.cfg.clone()
	app := &next.Apps[i]

	if upd.Name != nil {
		name := strings.TrimSpace(*upd.Name)
		if name == "" {
			return App{}, fmt.Errorf("%w: name não pode ficar vazio", ErrInvalid)
		}
		app.Name = name
	}
	if upd.Path != nil {
		path, removed := cleanPath(*upd.Path)
		if path == "" {
			return App{}, fmt.Errorf("%w: path não pode ficar vazio", ErrInvalid)
		}
		if len(removed) > 0 {
			s.log.Warn("caractere invisível removido do path do atalho",
				"id", id, "removidos", describeRunes(removed))
		}
		app.Path = path
	}
	if upd.Args != nil {
		if len(*upd.Args) == 0 {
			app.Args = nil
		} else {
			app.Args = append([]string(nil), *upd.Args...)
		}
	}

	if err := s.persist(next); err != nil {
		return App{}, err
	}
	s.cfg = next

	s.log.Info("atalho atualizado", "id", app.ID, "name", app.Name)
	return next.clone().Apps[i], nil
}

// DeleteApp remove um atalho.
func (s *Store) DeleteApp(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	i := s.cfg.indexOf(id)
	if i < 0 {
		return fmt.Errorf("%w: %s", ErrNotFound, id)
	}

	next := s.cfg.clone()
	next.Apps = append(next.Apps[:i], next.Apps[i+1:]...)
	if err := s.persist(next); err != nil {
		return err
	}
	s.cfg = next

	s.log.Info("atalho removido", "id", id)
	return nil
}
