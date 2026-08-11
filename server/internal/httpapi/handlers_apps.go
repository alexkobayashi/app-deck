package httpapi

import (
	"errors"
	"fmt"
	"net/http"

	"github.com/alexkobayashi/app-deck/server/internal/config"
)

// appDTO é a representação de um atalho na API. Note que não existe campo
// de ícone: o servidor só conhece nome e caminho, o ícone é escolhido e
// guardado no app Android.
type appDTO struct {
	ID   string   `json:"id"`
	Name string   `json:"name"`
	Path string   `json:"path"`
	Args []string `json:"args,omitempty"`
}

type listAppsResponse struct {
	Apps []appDTO `json:"apps"`
}

type launchResponse struct {
	Status string `json:"status"`
	ID     string `json:"id"`
	Name   string `json:"name"`
}

// appRequest usa ponteiros para distinguir "campo ausente" de "campo
// enviado vazio": no PUT, ausente significa "mantenha como está".
type appRequest struct {
	Name *string   `json:"name"`
	Path *string   `json:"path"`
	Args *[]string `json:"args"`
}

func toDTO(a config.App) appDTO {
	return appDTO{ID: a.ID, Name: a.Name, Path: a.Path, Args: a.Args}
}

// listApps devolve os atalhos na ordem do config.json, que é a ordem em
// que o deck os apresenta.
func (a *API) listApps(w http.ResponseWriter, _ *http.Request) {
	apps := a.Store.Apps()
	out := make([]appDTO, 0, len(apps))
	for _, app := range apps {
		out = append(out, toDTO(app))
	}
	writeJSON(w, http.StatusOK, listAppsResponse{Apps: out})
}

func (a *API) createApp(w http.ResponseWriter, r *http.Request) {
	var req appRequest
	if err := decodeJSON(w, r, &req); err != nil {
		respondDecodeError(w, err)
		return
	}
	if req.Name == nil || req.Path == nil {
		writeError(w, http.StatusBadRequest, CodeValidationError,
			"name e path são obrigatórios")
		return
	}

	var args []string
	if req.Args != nil {
		args = *req.Args
	}

	app, err := a.Store.AddApp(*req.Name, *req.Path, args)
	if err != nil {
		a.respondStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, toDTO(app))
}

func (a *API) updateApp(w http.ResponseWriter, r *http.Request) {
	var req appRequest
	if err := decodeJSON(w, r, &req); err != nil {
		respondDecodeError(w, err)
		return
	}
	if req.Name == nil && req.Path == nil && req.Args == nil {
		writeError(w, http.StatusBadRequest, CodeValidationError,
			"informe pelo menos um campo para alterar (name, path ou args)")
		return
	}

	app, err := a.Store.UpdateApp(r.PathValue("id"), config.AppUpdate{
		Name: req.Name,
		Path: req.Path,
		Args: req.Args,
	})
	if err != nil {
		a.respondStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, toDTO(app))
}

func (a *API) deleteApp(w http.ResponseWriter, r *http.Request) {
	if err := a.Store.DeleteApp(r.PathValue("id")); err != nil {
		a.respondStoreError(w, err)
		return
	}
	writeNoContent(w)
}

// launchApp abre o programa do atalho. Responde assim que o processo é
// criado, sem esperar o programa fechar.
func (a *API) launchApp(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	app, ok := a.Store.AppByID(id)
	if !ok {
		writeError(w, http.StatusNotFound, CodeNotFound, "atalho não encontrado: "+id)
		return
	}

	if err := a.Launcher.Launch(r.Context(), app.Path, app.Args...); err != nil {
		a.logger().Error("falha ao abrir programa",
			"id", id, "name", app.Name, "path", app.Path, "err", err)
		writeError(w, http.StatusInternalServerError, CodeLaunchFailed,
			fmt.Sprintf("não foi possível abrir %s: %v", app.Name, err))
		return
	}

	writeJSON(w, http.StatusOK, launchResponse{
		Status: "launched",
		ID:     app.ID,
		Name:   app.Name,
	})
}

// respondStoreError traduz os erros do Store para status HTTP.
func (a *API) respondStoreError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, config.ErrNotFound):
		writeError(w, http.StatusNotFound, CodeNotFound, err.Error())
	case errors.Is(err, config.ErrInvalid):
		writeError(w, http.StatusBadRequest, CodeValidationError, err.Error())
	default:
		// Sobrou o caso de falha ao gravar o config.json. O detalhe vai
		// para o log; o cliente recebe algo acionável.
		a.logger().Error("falha ao salvar a configuração", "err", err)
		writeError(w, http.StatusInternalServerError, CodeSaveFailed,
			"não foi possível salvar a configuração no servidor")
	}
}
