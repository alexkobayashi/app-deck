package pairing

import (
	"bytes"
	"encoding/json"
	"image/png"
	"net"
	"os"
	"path/filepath"
	"testing"
)

func validPayload() Payload {
	return Build(net.ParseIP("192.168.0.10"), 5050, "um-token-de-pareamento-bem-comprido")
}

func TestBuildAndValidate(t *testing.T) {
	p := validPayload()
	if p.IP != "192.168.0.10" || p.Port != 5050 {
		t.Fatalf("payload = %+v", p)
	}
	if err := p.Validate(); err != nil {
		t.Fatalf("Validate: %v", err)
	}
	if got := p.URL(); got != "http://192.168.0.10:5050" {
		t.Errorf("URL() = %q", got)
	}
}

func TestValidateRejectsUnusablePayloads(t *testing.T) {
	tests := []struct {
		name string
		p    Payload
	}{
		// Acontece de verdade quando o PC está sem rede: não faz sentido
		// mostrar um QR que o app não conseguiria usar.
		{"sem ip", Build(nil, 5050, "token-comprido-o-suficiente-aqui")},
		{"ip inválido", Payload{IP: "não-é-ip", Port: 5050, Token: "abc"}},
		{"porta zero", Payload{IP: "192.168.0.10", Port: 0, Token: "abc"}},
		{"porta fora do intervalo", Payload{IP: "192.168.0.10", Port: 70000, Token: "abc"}},
		{"token vazio", Payload{IP: "192.168.0.10", Port: 5050}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if err := tc.p.Validate(); err == nil {
				t.Error("Validate aceitou um payload inutilizável")
			}
			if _, err := PNG(tc.p, DefaultSize); err == nil {
				t.Error("PNG gerou um QR para um payload inutilizável")
			}
		})
	}
}

// O formato do JSON é contrato com o app Android: exatamente ip, port e
// token, com port como número.
func TestJSONShape(t *testing.T) {
	raw, err := validPayload().JSON()
	if err != nil {
		t.Fatal(err)
	}

	var generic map[string]any
	if err := json.Unmarshal(raw, &generic); err != nil {
		t.Fatalf("JSON inválido: %v", err)
	}
	if len(generic) != 3 {
		t.Errorf("o payload tem %d campos, quero 3: %s", len(generic), raw)
	}
	for _, k := range []string{"ip", "port", "token"} {
		if _, ok := generic[k]; !ok {
			t.Errorf("campo %q ausente: %s", k, raw)
		}
	}
	if _, ok := generic["port"].(float64); !ok {
		t.Errorf("port deveria ser número, veio %T: %s", generic["port"], raw)
	}
}

func TestPNGIsDecodableImage(t *testing.T) {
	data, err := PNG(validPayload(), 256)
	if err != nil {
		t.Fatalf("PNG: %v", err)
	}

	img, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		t.Fatalf("o resultado não é um PNG válido: %v", err)
	}
	b := img.Bounds()
	if b.Dx() < 256 || b.Dy() < 256 {
		t.Errorf("dimensões = %dx%d, esperava ao menos 256x256", b.Dx(), b.Dy())
	}
}

func TestPNGUsesDefaultSizeWhenZero(t *testing.T) {
	data, err := PNG(validPayload(), 0)
	if err != nil {
		t.Fatal(err)
	}
	img, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		t.Fatal(err)
	}
	if img.Bounds().Dx() < DefaultSize {
		t.Errorf("largura = %d, esperava ao menos %d", img.Bounds().Dx(), DefaultSize)
	}
}

func TestWriteFileAndRemove(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "AppDeck")

	path, err := WriteFile(validPayload(), dir, 256)
	if err != nil {
		t.Fatalf("WriteFile: %v", err)
	}
	if filepath.Base(path) != FileName {
		t.Errorf("nome do arquivo = %q, quero %q", filepath.Base(path), FileName)
	}

	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("arquivo não foi criado: %v", err)
	}
	if info.Size() == 0 {
		t.Error("arquivo vazio")
	}

	// O PNG carrega o token em claro, então não pode sobreviver ao
	// encerramento do servidor.
	if err := Remove(dir); err != nil {
		t.Fatalf("Remove: %v", err)
	}
	if _, err := os.Stat(path); !os.IsNotExist(err) {
		t.Errorf("arquivo continua existindo depois do Remove: %v", err)
	}

	// Remover duas vezes não é erro.
	if err := Remove(dir); err != nil {
		t.Errorf("Remove idempotente: %v", err)
	}
}

func TestWriteFileRejectsInvalidPayload(t *testing.T) {
	dir := t.TempDir()
	if _, err := WriteFile(Build(nil, 5050, "token"), dir, 256); err == nil {
		t.Fatal("WriteFile aceitou payload inválido")
	}
	if _, err := os.Stat(filepath.Join(dir, FileName)); !os.IsNotExist(err) {
		t.Error("um payload inválido não deveria criar arquivo")
	}
}
