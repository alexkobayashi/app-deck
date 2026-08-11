// Package pairing gera o QR code que pareia o app Android com o servidor.
//
// O objetivo é o usuário não precisar digitar IP, porta e um token de 43
// caracteres no celular: ele escaneia o QR e o app se configura sozinho.
package pairing

import (
	"encoding/json"
	"fmt"
	"net"
	"os"
	"path/filepath"

	qrcode "github.com/skip2/go-qrcode"
)

// FileName é o nome do PNG gerado.
const FileName = "pairing.png"

// DefaultSize é o lado do QR em pixels — grande o bastante para ser lido de
// um monitor por uma câmera de celular.
const DefaultSize = 512

// Payload é o conteúdo do QR code. O formato é parte do contrato com o app
// Android e está documentado em docs/api.md.
type Payload struct {
	IP    string `json:"ip"`
	Port  int    `json:"port"`
	Token string `json:"token"`
}

// Build monta o payload. O ip pode ser nil quando nenhuma interface de rede
// utilizável foi encontrada — nesse caso Validate recusa.
func Build(ip net.IP, port int, token string) Payload {
	p := Payload{Port: port, Token: token}
	if ip != nil {
		p.IP = ip.String()
	}
	return p
}

// Validate recusa payloads que o app não conseguiria usar.
func (p Payload) Validate() error {
	if p.IP == "" {
		return fmt.Errorf("nenhum endereço IPv4 na rede local foi encontrado; " +
			"verifique se o PC está conectado ao Wi-Fi ou cabo")
	}
	if net.ParseIP(p.IP) == nil {
		return fmt.Errorf("endereço IP inválido: %q", p.IP)
	}
	if p.Port < 1 || p.Port > 65535 {
		return fmt.Errorf("porta inválida: %d", p.Port)
	}
	if p.Token == "" {
		return fmt.Errorf("token vazio")
	}
	return nil
}

// JSON serializa o payload no formato que o app espera ler do QR.
func (p Payload) JSON() ([]byte, error) {
	return json.Marshal(p)
}

// URL é o endereço do servidor, útil para mostrar ao lado do QR.
func (p Payload) URL() string {
	return fmt.Sprintf("http://%s:%d", p.IP, p.Port)
}

// PNG codifica o payload como um QR code PNG.
func PNG(p Payload, size int) ([]byte, error) {
	if err := p.Validate(); err != nil {
		return nil, err
	}
	if size <= 0 {
		size = DefaultSize
	}
	data, err := p.JSON()
	if err != nil {
		return nil, fmt.Errorf("serializar payload de pareamento: %w", err)
	}
	png, err := qrcode.Encode(string(data), qrcode.Medium, size)
	if err != nil {
		return nil, fmt.Errorf("gerar QR code: %w", err)
	}
	return png, nil
}

// WriteFile grava o QR em dir/pairing.png e devolve o caminho.
//
// O arquivo contém o token em claro, por isso é gravado com permissão
// restrita e deve ser apagado com Remove ao encerrar o servidor.
func WriteFile(p Payload, dir string, size int) (string, error) {
	png, err := PNG(p, size)
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", fmt.Errorf("criar diretório %s: %w", dir, err)
	}
	path := filepath.Join(dir, FileName)
	if err := os.WriteFile(path, png, 0o600); err != nil {
		return "", fmt.Errorf("gravar %s: %w", path, err)
	}
	return path, nil
}

// Remove apaga o PNG de pareamento. Um arquivo já inexistente não é erro.
func Remove(dir string) error {
	err := os.Remove(filepath.Join(dir, FileName))
	if os.IsNotExist(err) {
		return nil
	}
	return err
}

// DefaultDir é %LOCALAPPDATA%\AppDeck no Windows.
func DefaultDir() (string, error) {
	base, err := os.UserCacheDir()
	if err != nil {
		return "", fmt.Errorf("localizar diretório local do usuário: %w", err)
	}
	return filepath.Join(base, "AppDeck"), nil
}

// Show grava o QR e o abre no visualizador de imagens padrão do sistema.
func Show(p Payload, dir string, size int) (string, error) {
	path, err := WriteFile(p, dir, size)
	if err != nil {
		return "", err
	}
	if err := openFile(path); err != nil {
		// O arquivo existe; não conseguir abrir o visualizador é recuperável
		// — o usuário pode abrir manualmente pelo caminho reportado.
		return path, err
	}
	return path, nil
}
