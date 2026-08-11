package autostart

import "testing"

func TestQuoteCommand(t *testing.T) {
	tests := []struct {
		name string
		exe  string
		args []string
		want string
	}{
		{
			// Sem as aspas, o Windows tentaria executar "C:\Program".
			name: "caminho com espaços",
			exe:  `C:\Program Files\AppDeck\app-deck-server.exe`,
			want: `"C:\Program Files\AppDeck\app-deck-server.exe"`,
		},
		{
			name: "argumento simples",
			exe:  `C:\deck.exe`,
			args: []string{"--port", "5050"},
			want: `"C:\deck.exe" --port 5050`,
		},
		{
			name: "argumento com espaço é citado",
			exe:  `C:\deck.exe`,
			args: []string{"--config", `C:\Users\Alex Kobayashi\config.json`},
			want: `"C:\deck.exe" --config "C:\Users\Alex Kobayashi\config.json"`,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := quoteCommand(tc.exe, tc.args); got != tc.want {
				t.Errorf("quoteCommand:\n  tenho %s\n  quero %s", got, tc.want)
			}
		})
	}
}
