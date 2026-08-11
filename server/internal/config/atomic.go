package config

import (
	"fmt"
	"os"
	"path/filepath"
)

// writeFileAtomic grava data em path sem nunca deixar o arquivo original
// num estado parcial.
//
// A sequência é: criar um temporário no mesmo diretório (rename só é
// atômico dentro do mesmo volume) → escrever → Sync → Close → Rename por
// cima do destino. Se qualquer passo falhar, o temporário é removido e o
// arquivo original permanece intacto.
//
// No Windows os.Rename usa MoveFileEx com MOVEFILE_REPLACE_EXISTING, logo
// sobrescrever um arquivo existente funciona. Não há fsync de diretório
// no Windows; aceitável para este caso de uso.
func writeFileAtomic(path string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(path)

	tmp, err := os.CreateTemp(dir, ".appdeck-*.tmp")
	if err != nil {
		return fmt.Errorf("criar arquivo temporário em %s: %w", dir, err)
	}
	tmpName := tmp.Name()

	committed := false
	defer func() {
		if !committed {
			tmp.Close()
			os.Remove(tmpName)
		}
	}()

	if _, err := tmp.Write(data); err != nil {
		return fmt.Errorf("escrever %s: %w", tmpName, err)
	}
	if err := tmp.Sync(); err != nil {
		return fmt.Errorf("sincronizar %s: %w", tmpName, err)
	}
	if err := tmp.Close(); err != nil {
		return fmt.Errorf("fechar %s: %w", tmpName, err)
	}
	if err := os.Chmod(tmpName, perm); err != nil {
		return fmt.Errorf("ajustar permissão de %s: %w", tmpName, err)
	}
	if err := os.Rename(tmpName, path); err != nil {
		return fmt.Errorf("mover %s para %s: %w", tmpName, path, err)
	}

	committed = true
	return nil
}
