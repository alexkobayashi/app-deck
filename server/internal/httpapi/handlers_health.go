package httpapi

import "net/http"

// healthResponse é deliberadamente mínimo: esta é a única rota sem
// autenticação, então não expõe hostname, caminhos nem a lista de atalhos.
type healthResponse struct {
	Status  string `json:"status"`
	Name    string `json:"name"`
	Version string `json:"version"`
}

// health responde o status do servidor. Usado pelo app para o indicador
// de conexão e para validar o pareamento antes de salvar a configuração.
func (a *API) health(w http.ResponseWriter, _ *http.Request) {
	v := a.Version
	if v == "" {
		v = "dev"
	}
	writeJSON(w, http.StatusOK, healthResponse{
		Status:  "ok",
		Name:    "app-deck",
		Version: v,
	})
}
