// Package token gera e compara os tokens de autenticação do servidor.
package token

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
)

// Bytes é a quantidade de entropia usada em cada token gerado.
const Bytes = 32

// MinLength é o tamanho abaixo do qual um token é considerado fraco.
//
// Generate produz 43 caracteres; qualquer coisa menor que isso foi digitada
// à mão e provavelmente é uma senha adivinhável. O valor só serve para
// emitir aviso — nenhum token configurado é recusado.
const MinLength = 32

// Generate devolve um token aleatório seguro, codificado em base64 URL-safe
// sem padding (comprimento fixo de 43 caracteres para Bytes = 32).
func Generate() (string, error) {
	buf := make([]byte, Bytes)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("gerar token aleatório: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

// Equal compara dois tokens em tempo constante, evitando vazar informação
// sobre o token correto através do tempo de resposta.
func Equal(got, want string) bool {
	if want == "" {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

// Weak indica que o token é curto o bastante para ser adivinhável.
func Weak(t string) bool {
	return len(t) < MinLength
}
