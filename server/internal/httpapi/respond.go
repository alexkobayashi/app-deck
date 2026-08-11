package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
)

// maxBodyBytes limita o corpo aceito nas rotas de escrita. Um atalho é
// só nome + caminho; 64 KiB já é folgado.
const maxBodyBytes = 64 << 10

// Códigos de erro estáveis, pensados para o app Android decidir o que
// fazer sem precisar interpretar a mensagem em português.
const (
	CodeInvalidJSON     = "invalid_json"
	CodeValidationError = "validation_error"
	CodeUnauthorized    = "unauthorized"
	CodeNotFound        = "not_found"
	CodePayloadTooLarge = "payload_too_large"
	CodeLaunchFailed    = "launch_failed"
	CodeSaveFailed      = "save_failed"
	CodeInternal        = "internal_error"
)

const contentTypeJSON = "application/json; charset=utf-8"

// errorResponse é o corpo de toda resposta de erro da API.
type errorResponse struct {
	Error string `json:"error"`
	Code  string `json:"code,omitempty"`
}

// writeJSON serializa antes de escrever o status, para nunca emitir um
// corpo pela metade se o Marshal falhar.
func writeJSON(w http.ResponseWriter, status int, v any) {
	buf, err := json.Marshal(v)
	if err != nil {
		w.Header().Set("Content-Type", contentTypeJSON)
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"error":"erro interno ao serializar a resposta","code":"internal_error"}`))
		return
	}
	w.Header().Set("Content-Type", contentTypeJSON)
	w.WriteHeader(status)
	_, _ = w.Write(buf)
}

// writeError responde com o formato de erro padrão da API.
func writeError(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, errorResponse{Error: msg, Code: code})
}

// writeNoContent responde 204, sem corpo.
func writeNoContent(w http.ResponseWriter) {
	w.WriteHeader(http.StatusNoContent)
}

// decodeJSON lê o corpo da requisição com limite de tamanho.
//
// DisallowUnknownFields fica desligado de propósito: uma versão nova do
// app pode mandar campos que este servidor ainda não conhece sem que a
// requisição quebre.
func decodeJSON(w http.ResponseWriter, r *http.Request, dst any) error {
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	dec := json.NewDecoder(r.Body)
	if err := dec.Decode(dst); err != nil {
		return err
	}
	if dec.More() {
		return errors.New("conteúdo extra depois do objeto JSON")
	}
	return nil
}

// respondDecodeError traduz a falha de leitura do corpo para a resposta
// HTTP correspondente.
func respondDecodeError(w http.ResponseWriter, err error) {
	var tooLarge *http.MaxBytesError
	if errors.As(err, &tooLarge) {
		writeError(w, http.StatusRequestEntityTooLarge, CodePayloadTooLarge,
			fmt.Sprintf("corpo da requisição maior que %d bytes", maxBodyBytes))
		return
	}
	writeError(w, http.StatusBadRequest, CodeInvalidJSON, "corpo da requisição não é um JSON válido")
}
