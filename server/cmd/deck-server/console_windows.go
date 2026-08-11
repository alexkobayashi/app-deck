//go:build windows

package main

import "syscall"

// cpUTF8 é a codepage UTF-8 do Windows.
const cpUTF8 = 65001

// configureConsole põe o console em UTF-8.
//
// O console do Windows usa por padrão uma codepage OEM (437/850), na qual
// as mensagens de log em português saem como "configuraÃ§Ã£o". Como o log
// é a principal ferramenta de diagnóstico deste servidor, vale a chamada.
// Uma falha aqui é irrelevante: o texto sai feio, nada mais.
// kernel32 está na lista de KnownDLLs do Windows, então é sempre resolvida
// a partir de System32 — não há risco de DLL planting aqui.
func configureConsole() {
	kernel32 := syscall.NewLazyDLL("kernel32.dll")
	setConsoleOutputCP := kernel32.NewProc("SetConsoleOutputCP")
	_, _, _ = setConsoleOutputCP.Call(cpUTF8)
}
