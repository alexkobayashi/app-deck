package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Montar as entradas por concatenação com string(rune(...)) em vez de
// escrever o caractere no literal: um invisível dentro do arquivo de teste
// seria impossível de revisar e fácil de perder num merge.
const (
	lre = string(rune(0x202A)) // LEFT-TO-RIGHT EMBEDDING — o do Windows
	rlo = string(rune(0x202E)) // RIGHT-TO-LEFT OVERRIDE — disfarce de extensão
	zwj = string(rune(0x200D)) // ZERO WIDTH JOINER — deve ser preservado
	bom = string(rune(0xFEFF))
	zws = string(rune(0x200B))
)

func TestCleanPath(t *testing.T) {
	const windowsPath = `C:\Users\alex\OneDrive\Área de Trabalho\Spotify.lnk`

	tests := []struct {
		name        string
		in          string
		want        string
		wantRemoved int
	}{
		{
			// O caso real: caminho copiado da caixa de Propriedades.
			name:        "U+202A no início",
			in:          lre + windowsPath,
			want:        windowsPath,
			wantRemoved: 1,
		},
		{
			name:        "caminho limpo passa intacto",
			in:          windowsPath,
			want:        windowsPath,
			wantRemoved: 0,
		},
		{
			name:        "espaços nas pontas continuam sendo aparados",
			in:          "  " + windowsPath + "\t",
			want:        windowsPath,
			wantRemoved: 0,
		},
		{
			// Ordem importa: remover primeiro, aparar depois. Se fosse ao
			// contrário, o espaço ficaria preso atrás do invisível.
			name:        "invisível antes de espaço",
			in:          lre + "   " + windowsPath,
			want:        windowsPath,
			wantRemoved: 1,
		},
		{
			name:        "RLO no meio (disfarce de extensão)",
			in:          `C:\tmp\foto` + rlo + `gnp.exe`,
			want:        `C:\tmp\fotognp.exe`,
			wantRemoved: 1,
		},
		{
			name:        "vários invisíveis diferentes",
			in:          bom + `C:\a` + zws + `\b.exe`,
			want:        `C:\a\b.exe`,
			wantRemoved: 2,
		},
		{
			// ZWJ tem uso linguístico legítimo; não é nosso para remover.
			name:        "ZWJ é preservado de propósito",
			in:          `C:\tmp\a` + zwj + `b.exe`,
			want:        `C:\tmp\a` + zwj + `b.exe`,
			wantRemoved: 0,
		},
		{
			name:        "só invisível vira string vazia",
			in:          lre + "  ",
			want:        "",
			wantRemoved: 1,
		},
		{
			// Acentos e não-ASCII normais não podem ser tocados.
			name:        "acentos sobrevivem",
			in:          `C:\Área de Trabalho\Programação.exe`,
			want:        `C:\Área de Trabalho\Programação.exe`,
			wantRemoved: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, removed := cleanPath(tt.in)
			if got != tt.want {
				t.Errorf("cleanPath() = %q, quero %q", got, tt.want)
			}
			if len(removed) != tt.wantRemoved {
				t.Errorf("removeu %d runes (%s), quero %d",
					len(removed), describeRunes(removed), tt.wantRemoved)
			}
		})
	}
}

func TestDescribeRunes(t *testing.T) {
	got := describeRunes([]rune{0x202A, 0xFEFF})
	if want := "U+202A, U+FEFF"; got != want {
		t.Errorf("describeRunes() = %q, quero %q", got, want)
	}
}

// O aviso precisa nomear o code point e mostrar o caminho de forma que o
// invisível apareça — senão o usuário lê "path errado" olhando para um path
// que parece certo.
func TestInvisibleCharWarningRevelaOCaractere(t *testing.T) {
	w := invisibleCharWarning("abc123", lre+`C:\x.exe`, []rune{0x202A})

	if !strings.Contains(w.Msg, "U+202A") {
		t.Errorf("aviso não nomeia o code point: %s", w.Msg)
	}
	// %q escapa o não imprimível, então o invisível vira texto visível.
	if !strings.Contains(w.Msg, `\u202a`) {
		t.Errorf("aviso não revela o caractere no path: %s", w.Msg)
	}
	if w.Field != "path" || w.AppID != "abc123" {
		t.Errorf("aviso mal preenchido: %+v", w)
	}
}

// --- Integração ---
//
// Os testes acima exercitam a função pura. Estes exercitam os caminhos que o
// usuário realmente percorre. A distinção não é acadêmica: o bug equivalente
// no app Android passou por testes de função pura e só aparecia no pipeline
// completo.

// Um config.json que já está em disco com o caractere invisível — o estado em
// que a máquina do usuário estava — precisa se curar sozinho no próximo start.
func TestOpenLimpaPathInvisivelERegravaOArquivo(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, FileName)
	const alvo = `C:\Windows\System32\calc.exe`

	raw, err := json.Marshal(Config{
		Version: CurrentVersion,
		Token:   strings.Repeat("t", 40),
		Port:    DefaultPort,
		Bind:    DefaultBind,
		Apps:    []App{{ID: "abcdef0123456789", Name: "Calculadora", Path: lre + alvo}},
	})
	if err != nil {
		t.Fatalf("montar fixture: %v", err)
	}
	if err := os.WriteFile(path, raw, 0o600); err != nil {
		t.Fatalf("gravar fixture: %v", err)
	}

	store, warns, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("Open: %v", err)
	}

	// 1. Em memória, limpo.
	apps := store.Apps()
	if len(apps) != 1 {
		t.Fatalf("esperava 1 atalho, veio %d", len(apps))
	}
	if apps[0].Path != alvo {
		t.Errorf("path em memória = %q, quero %q", apps[0].Path, alvo)
	}

	// 2. Em disco, limpo — senão o problema volta no próximo start.
	if got := readBack(t, store).Apps[0].Path; got != alvo {
		t.Errorf("path em disco = %q, quero %q", got, alvo)
	}

	// 3. O usuário foi avisado. Limpar em silêncio esconderia de onde veio.
	var avisou bool
	for _, w := range warns {
		if w.Field == "path" && strings.Contains(w.Msg, "U+202A") {
			avisou = true
		}
	}
	if !avisou {
		t.Errorf("nenhum aviso mencionando U+202A; avisos: %v", warns)
	}

	// 4. Reabrir não deve gerar o aviso de novo: já está curado.
	_, warns2, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("segundo Open: %v", err)
	}
	for _, w := range warns2 {
		if strings.Contains(w.Msg, "U+202A") {
			t.Errorf("aviso reapareceu no segundo Open: %s", w.Msg)
		}
	}
}

func TestAddAppLimpaPathInvisivel(t *testing.T) {
	store := newTestStore(t)
	const alvo = `C:\Windows\System32\notepad.exe`

	app, err := store.AddApp("Bloco de Notas", lre+alvo, nil)
	if err != nil {
		t.Fatalf("AddApp: %v", err)
	}
	if app.Path != alvo {
		t.Errorf("path devolvido = %q, quero %q", app.Path, alvo)
	}
	if got := readBack(t, store).Apps[0].Path; got != alvo {
		t.Errorf("path persistido = %q, quero %q", got, alvo)
	}
}

func TestUpdateAppLimpaPathInvisivel(t *testing.T) {
	store := newTestStore(t)
	const alvo = `C:\Windows\explorer.exe`

	app, err := store.AddApp("Explorador", `C:\antigo.exe`, nil)
	if err != nil {
		t.Fatalf("AddApp: %v", err)
	}

	sujo := lre + alvo
	updated, err := store.UpdateApp(app.ID, AppUpdate{Path: &sujo})
	if err != nil {
		t.Fatalf("UpdateApp: %v", err)
	}
	if updated.Path != alvo {
		t.Errorf("path devolvido = %q, quero %q", updated.Path, alvo)
	}
	if got := readBack(t, store).Apps[0].Path; got != alvo {
		t.Errorf("path persistido = %q, quero %q", got, alvo)
	}
}

// Um path que só tem invisível e espaço é o mesmo que vazio, e tem que ser
// recusado — não gravado como string vazia.
func TestAddAppRecusaPathSoComInvisivel(t *testing.T) {
	store := newTestStore(t)

	if _, err := store.AddApp("Fantasma", lre+"   ", nil); err == nil {
		t.Error("esperava erro para path composto só de invisível e espaço")
	}
	if apps := store.Apps(); len(apps) != 0 {
		t.Errorf("nada deveria ter sido gravado, veio %d atalhos", len(apps))
	}
}
