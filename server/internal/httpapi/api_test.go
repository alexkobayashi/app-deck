package httpapi

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"

	"github.com/alexkobayashi/app-deck/server/internal/config"
	"github.com/alexkobayashi/app-deck/server/internal/launcher"
)

const calcPath = `C:\Windows\System32\calc.exe`

type fixture struct {
	api     *API
	store   *config.Store
	fake    *launcher.Fake
	handler http.Handler
	calc    config.App
	chrome  config.App
}

func newFixture(t *testing.T) *fixture {
	t.Helper()

	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	// Store real sobre um diretório temporário: os testes de escrita
	// verificam de fato a persistência, não um mock.
	store, _, err := config.Open(filepath.Join(t.TempDir(), config.FileName), log)
	if err != nil {
		t.Fatalf("abrir store: %v", err)
	}

	calc, err := store.AddApp("Calculadora", calcPath, nil)
	if err != nil {
		t.Fatal(err)
	}
	chrome, err := store.AddApp("Chrome", `C:\Program Files\Google\Chrome\Application\chrome.exe`, []string{"--incognito"})
	if err != nil {
		t.Fatal(err)
	}

	fake := &launcher.Fake{}
	api := &API{Store: store, Launcher: fake, Log: log, Version: "v-test"}

	return &fixture{
		api:     api,
		store:   store,
		fake:    fake,
		handler: api.Handler(),
		calc:    calc,
		chrome:  chrome,
	}
}

// do executa uma requisição autenticada.
func (f *fixture) do(method, path, body string) *httptest.ResponseRecorder {
	return f.doRaw(method, path, body, "Bearer "+f.store.Token())
}

func (f *fixture) doRaw(method, path, body, authHeader string) *httptest.ResponseRecorder {
	var reader io.Reader
	if body != "" {
		reader = strings.NewReader(body)
	}
	req := httptest.NewRequest(method, path, reader)
	if authHeader != "" {
		req.Header.Set("Authorization", authHeader)
	}
	if body != "" {
		req.Header.Set("Content-Type", "application/json")
	}
	rec := httptest.NewRecorder()
	f.handler.ServeHTTP(rec, req)
	return rec
}

func assertStatus(t *testing.T, rec *httptest.ResponseRecorder, want int) {
	t.Helper()
	if rec.Code != want {
		t.Fatalf("status = %d, quero %d; corpo: %s", rec.Code, want, rec.Body.String())
	}
}

func assertJSONContentType(t *testing.T, rec *httptest.ResponseRecorder) {
	t.Helper()
	if ct := rec.Header().Get("Content-Type"); !strings.HasPrefix(ct, "application/json") {
		t.Errorf("Content-Type = %q, quero application/json", ct)
	}
}

func decodeBody[T any](t *testing.T, rec *httptest.ResponseRecorder) T {
	t.Helper()
	var out T
	if err := json.Unmarshal(rec.Body.Bytes(), &out); err != nil {
		t.Fatalf("resposta não é JSON válido (%v): %s", err, rec.Body.String())
	}
	return out
}

func assertErrorCode(t *testing.T, rec *httptest.ResponseRecorder, code string) {
	t.Helper()
	assertJSONContentType(t, rec)
	body := decodeBody[errorResponse](t, rec)
	if body.Code != code {
		t.Errorf("code = %q, quero %q (erro: %q)", body.Code, code, body.Error)
	}
	if body.Error == "" {
		t.Error("resposta de erro sem mensagem")
	}
}

func TestHealthIsPublic(t *testing.T) {
	f := newFixture(t)

	rec := f.doRaw(http.MethodGet, "/api/health", "", "")
	assertStatus(t, rec, http.StatusOK)
	assertJSONContentType(t, rec)

	body := decodeBody[healthResponse](t, rec)
	if body.Status != "ok" || body.Name != "app-deck" || body.Version != "v-test" {
		t.Errorf("resposta = %+v", body)
	}

	// A única rota sem autenticação não pode vazar nada sensível.
	raw := rec.Body.String()
	if strings.Contains(raw, f.store.Token()) {
		t.Fatal("o /api/health vazou o token")
	}
	for _, forbidden := range []string{"apps", "path", "config"} {
		if strings.Contains(raw, forbidden) {
			t.Errorf("o /api/health expõe %q: %s", forbidden, raw)
		}
	}
}

// Todas as rotas exceto /api/health exigem o Bearer correto.
func TestAuthOnProtectedRoutes(t *testing.T) {
	routes := []struct {
		method, path, body string
	}{
		{http.MethodGet, "/api/apps", ""},
		{http.MethodPost, "/api/apps", `{"name":"A","path":"C:\\a.exe"}`},
		{http.MethodPut, "/api/apps/{id}", `{"name":"B"}`},
		{http.MethodDelete, "/api/apps/{id}", ""},
		{http.MethodPost, "/api/apps/{id}/launch", ""},
	}

	headers := []struct {
		name       string
		build      func(token string) string
		authorized bool
	}{
		{"sem header", func(string) string { return "" }, false},
		{"esquema Basic", func(tk string) string { return "Basic " + tk }, false},
		{"sem esquema", func(tk string) string { return tk }, false},
		{"token errado", func(tk string) string { return "Bearer " + tk + "x" }, false},
		{"token vazio", func(string) string { return "Bearer " }, false},
		{"token truncado", func(tk string) string { return "Bearer " + tk[:len(tk)-1] }, false},
		{"esquema em minúsculas", func(tk string) string { return "bearer " + tk }, true},
		{"correto", func(tk string) string { return "Bearer " + tk }, true},
	}

	for _, r := range routes {
		for _, h := range headers {
			t.Run(r.method+" "+r.path+" | "+h.name, func(t *testing.T) {
				// Fixture novo por caso: PUT e DELETE alteram o estado.
				f := newFixture(t)
				path := strings.ReplaceAll(r.path, "{id}", f.calc.ID)

				rec := f.doRaw(r.method, path, r.body, h.build(f.store.Token()))
				if h.authorized {
					if rec.Code == http.StatusUnauthorized {
						t.Fatalf("requisição válida recusada: %s", rec.Body.String())
					}
					return
				}

				assertStatus(t, rec, http.StatusUnauthorized)
				assertErrorCode(t, rec, CodeUnauthorized)
				if got := rec.Header().Get("WWW-Authenticate"); !strings.HasPrefix(got, "Bearer") {
					t.Errorf("WWW-Authenticate = %q", got)
				}
			})
		}
	}
}

// O protótipo aceitava ?token=...; a v2 não pode voltar a aceitar.
func TestTokenInQueryStringIsRejected(t *testing.T) {
	f := newFixture(t)
	rec := f.doRaw(http.MethodGet, "/api/apps?token="+f.store.Token(), "", "")
	assertStatus(t, rec, http.StatusUnauthorized)
}

func TestListApps(t *testing.T) {
	f := newFixture(t)

	rec := f.do(http.MethodGet, "/api/apps", "")
	assertStatus(t, rec, http.StatusOK)
	assertJSONContentType(t, rec)

	body := decodeBody[listAppsResponse](t, rec)
	if len(body.Apps) != 2 {
		t.Fatalf("len(apps) = %d, quero 2", len(body.Apps))
	}
	if body.Apps[0].ID != f.calc.ID || body.Apps[0].Name != "Calculadora" || body.Apps[0].Path != calcPath {
		t.Errorf("primeiro app = %+v", body.Apps[0])
	}
	if len(body.Apps[1].Args) != 1 || body.Apps[1].Args[0] != "--incognito" {
		t.Errorf("args do segundo app = %v", body.Apps[1].Args)
	}

	// A listagem nunca pode carregar o token junto.
	if strings.Contains(rec.Body.String(), f.store.Token()) {
		t.Fatal("o /api/apps vazou o token")
	}
}

func TestListAppsEmptyIsArrayNotNull(t *testing.T) {
	f := newFixture(t)
	for _, app := range f.store.Apps() {
		if err := f.store.DeleteApp(app.ID); err != nil {
			t.Fatal(err)
		}
	}

	rec := f.do(http.MethodGet, "/api/apps", "")
	assertStatus(t, rec, http.StatusOK)
	// "apps": null quebraria clientes que iteram direto no array.
	if !strings.Contains(rec.Body.String(), `"apps":[]`) {
		t.Errorf("corpo = %s, quero apps como array vazio", rec.Body.String())
	}
}

func TestCreateApp(t *testing.T) {
	f := newFixture(t)

	rec := f.do(http.MethodPost, "/api/apps", `{"name":"Steam","path":"C:\\Steam\\steam.exe","args":["-silent"]}`)
	assertStatus(t, rec, http.StatusCreated)

	created := decodeBody[appDTO](t, rec)
	if created.ID == "" {
		t.Fatal("app criado sem id")
	}
	if created.Name != "Steam" || created.Path != `C:\Steam\steam.exe` {
		t.Errorf("app criado = %+v", created)
	}
	if len(created.Args) != 1 || created.Args[0] != "-silent" {
		t.Errorf("args = %v", created.Args)
	}

	// Persistiu de verdade?
	reopened, _, err := config.Open(f.store.Path(), slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := reopened.AppByID(created.ID); !ok {
		t.Error("o app criado não sobreviveu a uma reabertura do config")
	}
}

func TestCreateAppValidation(t *testing.T) {
	tests := []struct {
		name, body, code string
		status           int
	}{
		{"json inválido", `{`, CodeInvalidJSON, http.StatusBadRequest},
		{"corpo vazio", ``, CodeInvalidJSON, http.StatusBadRequest},
		{"lixo depois do objeto", `{"name":"A","path":"p"} {}`, CodeInvalidJSON, http.StatusBadRequest},
		{"sem name", `{"path":"C:\\a.exe"}`, CodeValidationError, http.StatusBadRequest},
		{"sem path", `{"name":"A"}`, CodeValidationError, http.StatusBadRequest},
		{"name em branco", `{"name":"   ","path":"C:\\a.exe"}`, CodeValidationError, http.StatusBadRequest},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			f := newFixture(t)
			rec := f.do(http.MethodPost, "/api/apps", tc.body)
			assertStatus(t, rec, tc.status)
			assertErrorCode(t, rec, tc.code)
			if len(f.store.Apps()) != 2 {
				t.Error("uma requisição inválida alterou o estado")
			}
		})
	}
}

func TestCreateAppRejectsHugeBody(t *testing.T) {
	f := newFixture(t)
	huge := `{"name":"A","path":"` + strings.Repeat("x", maxBodyBytes+1) + `"}`

	rec := f.do(http.MethodPost, "/api/apps", huge)
	assertStatus(t, rec, http.StatusRequestEntityTooLarge)
	assertErrorCode(t, rec, CodePayloadTooLarge)
}

// Campos desconhecidos são ignorados de propósito: uma versão nova do app
// pode mandar mais coisas sem quebrar num servidor antigo.
func TestCreateAppIgnoresUnknownFields(t *testing.T) {
	f := newFixture(t)
	rec := f.do(http.MethodPost, "/api/apps", `{"name":"A","path":"C:\\a.exe","icon":"🌐","futuro":true}`)
	assertStatus(t, rec, http.StatusCreated)
}

func TestUpdateApp(t *testing.T) {
	f := newFixture(t)

	rec := f.do(http.MethodPut, "/api/apps/"+f.calc.ID, `{"name":"Calc"}`)
	assertStatus(t, rec, http.StatusOK)

	updated := decodeBody[appDTO](t, rec)
	if updated.ID != f.calc.ID {
		t.Error("o id mudou numa edição; os ícones do app seriam perdidos")
	}
	if updated.Name != "Calc" || updated.Path != calcPath {
		t.Errorf("app atualizado = %+v", updated)
	}
}

func TestUpdateAppErrors(t *testing.T) {
	f := newFixture(t)

	rec := f.do(http.MethodPut, "/api/apps/nao-existe", `{"name":"X"}`)
	assertStatus(t, rec, http.StatusNotFound)
	assertErrorCode(t, rec, CodeNotFound)

	rec = f.do(http.MethodPut, "/api/apps/"+f.calc.ID, `{}`)
	assertStatus(t, rec, http.StatusBadRequest)
	assertErrorCode(t, rec, CodeValidationError)

	rec = f.do(http.MethodPut, "/api/apps/"+f.calc.ID, `{"name":""}`)
	assertStatus(t, rec, http.StatusBadRequest)
	assertErrorCode(t, rec, CodeValidationError)
}

func TestDeleteApp(t *testing.T) {
	f := newFixture(t)

	rec := f.do(http.MethodDelete, "/api/apps/"+f.calc.ID, "")
	assertStatus(t, rec, http.StatusNoContent)
	if rec.Body.Len() != 0 {
		t.Errorf("204 com corpo: %s", rec.Body.String())
	}
	if _, ok := f.store.AppByID(f.calc.ID); ok {
		t.Error("o app não foi removido")
	}

	rec = f.do(http.MethodDelete, "/api/apps/"+f.calc.ID, "")
	assertStatus(t, rec, http.StatusNotFound)
	assertErrorCode(t, rec, CodeNotFound)
}

func TestLaunchApp(t *testing.T) {
	f := newFixture(t)

	rec := f.do(http.MethodPost, "/api/apps/"+f.chrome.ID+"/launch", "")
	assertStatus(t, rec, http.StatusOK)

	body := decodeBody[launchResponse](t, rec)
	if body.Status != "launched" || body.ID != f.chrome.ID || body.Name != "Chrome" {
		t.Errorf("resposta = %+v", body)
	}

	calls := f.fake.Calls()
	if len(calls) != 1 {
		t.Fatalf("chamadas ao launcher = %d, quero 1", len(calls))
	}
	if calls[0].Path != f.chrome.Path {
		t.Errorf("path executado = %q, quero %q", calls[0].Path, f.chrome.Path)
	}
	if len(calls[0].Args) != 1 || calls[0].Args[0] != "--incognito" {
		t.Errorf("args executados = %v", calls[0].Args)
	}
}

func TestLaunchAppNotFound(t *testing.T) {
	f := newFixture(t)

	rec := f.do(http.MethodPost, "/api/apps/nao-existe/launch", "")
	assertStatus(t, rec, http.StatusNotFound)
	assertErrorCode(t, rec, CodeNotFound)
	if len(f.fake.Calls()) != 0 {
		t.Error("o launcher foi chamado para um id inexistente")
	}
}

func TestLaunchAppFailure(t *testing.T) {
	f := newFixture(t)
	f.fake.Err = launcher.ErrExecutableNotFound

	rec := f.do(http.MethodPost, "/api/apps/"+f.calc.ID+"/launch", "")
	assertStatus(t, rec, http.StatusInternalServerError)
	assertErrorCode(t, rec, CodeLaunchFailed)

	// A mensagem tem que ajudar o usuário a entender qual atalho falhou.
	if !strings.Contains(decodeBody[errorResponse](t, rec).Error, "Calculadora") {
		t.Errorf("mensagem de erro sem o nome do atalho: %s", rec.Body.String())
	}
}

func TestUnknownRoutesRespondJSON(t *testing.T) {
	f := newFixture(t)

	cases := []struct{ method, path string }{
		{http.MethodGet, "/"},
		{http.MethodGet, "/api/qualquer"},
		{http.MethodGet, "/api/apps/" + f.calc.ID},
		{http.MethodPatch, "/api/apps"},
		{http.MethodGet, "/api/apps/" + f.calc.ID + "/launch"},
	}
	for _, tc := range cases {
		t.Run(tc.method+" "+tc.path, func(t *testing.T) {
			rec := f.do(tc.method, tc.path, "")
			assertStatus(t, rec, http.StatusNotFound)
			assertErrorCode(t, rec, CodeNotFound)
		})
	}
}

func TestRecoverMiddlewareReturnsJSON(t *testing.T) {
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	h := Recover(log)(http.HandlerFunc(func(http.ResponseWriter, *http.Request) {
		panic("boom")
	}))

	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/apps", nil))

	assertStatus(t, rec, http.StatusInternalServerError)
	assertErrorCode(t, rec, CodeInternal)
}

func TestBearerToken(t *testing.T) {
	tests := []struct {
		header string
		want   string
		ok     bool
	}{
		{"Bearer abc", "abc", true},
		{"bearer abc", "abc", true},
		{"BEARER abc", "abc", true},
		{"Bearer   abc  ", "abc", true},
		{"Basic abc", "", false},
		{"abc", "", false},
		{"Bearer", "", false},
		{"Bearer ", "", false},
		{"", "", false},
	}
	for _, tc := range tests {
		t.Run(tc.header, func(t *testing.T) {
			got, ok := bearerToken(tc.header)
			if ok != tc.ok || got != tc.want {
				t.Errorf("bearerToken(%q) = (%q, %v), quero (%q, %v)", tc.header, got, ok, tc.want, tc.ok)
			}
		})
	}
}
