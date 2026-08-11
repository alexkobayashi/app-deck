package token

import "testing"

func TestGenerateIsUniqueAndLongEnough(t *testing.T) {
	const n = 1000
	seen := make(map[string]bool, n)
	for i := 0; i < n; i++ {
		tok, err := Generate()
		if err != nil {
			t.Fatalf("Generate: %v", err)
		}
		if len(tok) < 32 {
			t.Fatalf("token com %d caracteres: %q", len(tok), tok)
		}
		if Weak(tok) {
			t.Fatalf("token gerado classificado como fraco: %q", tok)
		}
		if seen[tok] {
			t.Fatalf("token repetido na iteração %d", i)
		}
		seen[tok] = true
	}
}

func TestGenerateIsURLSafe(t *testing.T) {
	// O token vai dentro de um JSON num QR code e num header HTTP: nada de
	// caracteres que precisem de escape.
	tok, err := Generate()
	if err != nil {
		t.Fatal(err)
	}
	for _, c := range tok {
		ok := (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			(c >= '0' && c <= '9') || c == '-' || c == '_'
		if !ok {
			t.Fatalf("caractere inesperado %q em %q", c, tok)
		}
	}
}

func TestEqual(t *testing.T) {
	tests := []struct {
		name      string
		got, want string
		expect    bool
	}{
		{"iguais", "abc123", "abc123", true},
		{"diferentes", "abc123", "abc124", false},
		{"prefixo", "abc", "abc123", false},
		{"caixa diferente", "ABC123", "abc123", false},
		{"esperado vazio recusa tudo", "qualquer", "", false},
		{"ambos vazios recusa", "", "", false},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := Equal(tc.got, tc.want); got != tc.expect {
				t.Errorf("Equal(%q, %q) = %v, quero %v", tc.got, tc.want, got, tc.expect)
			}
		})
	}
}

func TestWeak(t *testing.T) {
	// O token de exemplo do protótipo é exatamente o caso que o aviso
	// existe para pegar.
	if !Weak("troque-esta-senha-123") {
		t.Error("o token de exemplo do protótipo deveria ser considerado fraco")
	}
	if !Weak("curto") {
		t.Error("token curto deveria ser fraco")
	}
	if Weak("um-token-longo-o-suficiente-mesmo") {
		t.Error("token com 33 caracteres não deveria ser fraco")
	}
}
