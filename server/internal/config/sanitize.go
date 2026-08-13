package config

import (
	"fmt"
	"strings"
)

// Caracteres de formatação Unicode que não têm uso legítimo dentro de um
// caminho de arquivo.
//
// Escritos como números de propósito. Um literal de caractere aqui seria
// invisível no próprio código-fonte — que é exatamente o problema que este
// arquivo existe para resolver — e um deles (o BOM) chega a impedir o
// arquivo de compilar.
const (
	zeroWidthSpace     rune = 0x200B
	leftToRightMark    rune = 0x200E
	rightToLeftMark    rune = 0x200F
	firstBidiEmbedding rune = 0x202A // LEFT-TO-RIGHT EMBEDDING
	lastBidiEmbedding  rune = 0x202E // RIGHT-TO-LEFT OVERRIDE
	firstBidiIsolate   rune = 0x2066 // LEFT-TO-RIGHT ISOLATE
	lastBidiIsolate    rune = 0x2069 // POP DIRECTIONAL ISOLATE
	byteOrderMark      rune = 0xFEFF
)

// isInvisibleInPath diz se o rune é um desses caracteres.
//
// O caso que motivou isto: a caixa de *Propriedades* do Windows copia o
// caminho com um U+202A na frente. O usuário cola no app, o campo parece
// perfeito, o config.json parece perfeito, e o os.Stat falha com "executável
// não encontrado" apontando para um caminho que existe de verdade.
// strings.TrimSpace não resolve: U+202A é categoria Cf (formato), não espaço.
//
// Há também um lado de segurança. U+202E (RIGHT-TO-LEFT OVERRIDE) é o truque
// clássico de disfarce de extensão: um nome de arquivo que carregue esse
// caractere antes de "gnp.exe" aparece na tela como se terminasse em ".png".
// Removê-lo impede que o path exibido minta sobre o que será executado.
//
// U+200C e U+200D (ZWNJ e ZWJ) ficam de fora de propósito: têm uso
// linguístico real em persa, árabe e línguas índicas, e podem legitimamente
// fazer parte do nome de uma pasta.
func isInvisibleInPath(r rune) bool {
	switch {
	case r == zeroWidthSpace, r == byteOrderMark:
		return true
	case r == leftToRightMark, r == rightToLeftMark:
		return true
	case r >= firstBidiEmbedding && r <= lastBidiEmbedding:
		return true
	case r >= firstBidiIsolate && r <= lastBidiIsolate:
		return true
	}
	return false
}

// cleanPath remove os caracteres invisíveis e apara os espaços das pontas.
//
// Devolve o caminho limpo e a lista de runes removidos, para que o chamador
// possa avisar *qual* caractere estava lá — sem isso o aviso seria inútil, já
// que por definição o usuário não consegue ver o problema.
//
// Só o path passa por aqui. O `name` é texto livre exibido no deck, onde
// marcas bidirecionais podem ser exatamente o que o usuário quer num nome em
// árabe ou hebraico; e um nome errado é cosmético, enquanto um path errado
// impede o atalho de funcionar.
func cleanPath(p string) (string, []rune) {
	var removed []rune
	cleaned := strings.Map(func(r rune) rune {
		if isInvisibleInPath(r) {
			removed = append(removed, r)
			return -1
		}
		return r
	}, p)
	return strings.TrimSpace(cleaned), removed
}

// describeRunes formata runes como code points ("U+202A"), que é a única
// forma de nomear um caractere invisível numa mensagem.
func describeRunes(rs []rune) string {
	parts := make([]string, 0, len(rs))
	for _, r := range rs {
		parts = append(parts, fmt.Sprintf("U+%04X", r))
	}
	return strings.Join(parts, ", ")
}

// invisibleCharWarning monta o aviso padrão para um path que precisou de
// limpeza. O caminho original vai com %q de propósito: o verbo escapa os
// caracteres não imprimíveis, então o invisível finalmente aparece no log.
func invisibleCharWarning(appID, original string, removed []rune) Warning {
	return Warning{
		AppID: appID,
		Field: "path",
		Msg: fmt.Sprintf(
			"caractere invisível (%s) removido do path %q; costuma vir de copiar "+
				"o caminho pela caixa de Propriedades do Windows",
			describeRunes(removed), original),
	}
}
