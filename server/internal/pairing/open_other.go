//go:build !windows

package pairing

import "errors"

// openFile não tenta adivinhar o visualizador de imagens fora do Windows: o
// alvo do projeto é Windows, e o caminho do arquivo é reportado ao chamador.
func openFile(string) error {
	return errors.New("abrir o visualizador de imagens só é suportado no Windows")
}
