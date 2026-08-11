package config

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
)

// idBytes gera IDs de 16 caracteres hex. Espaço de 2^64: a chance de
// colisão é desprezível para as dezenas de atalhos de um deck.
const idBytes = 8

// NewID devolve um identificador opaco para um atalho.
//
// O valor é gerado uma única vez e nunca reescrito: o app Android usa o
// ID como chave da customização de ícone, então um ID instável (índice do
// array, hash do nome ou do caminho) faria o usuário perder os ícones a
// cada edição da configuração.
func NewID() (string, error) {
	buf := make([]byte, idBytes)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("gerar id aleatório: %w", err)
	}
	return hex.EncodeToString(buf), nil
}
