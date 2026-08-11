//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

const (
	mbOK          = 0x00000000
	mbIconError   = 0x00000010
	mbSystemModal = 0x00001000
)

// reportFatal mostra o erro numa caixa de diálogo.
//
// Compilado com -H=windowsgui o processo não tem stdout nem stderr: sem
// isso, um config.json inválido ou uma porta ocupada faria o servidor
// simplesmente não aparecer, sem nenhuma pista para o usuário.
//
// user32.dll está na lista de KnownDLLs do Windows, então é sempre resolvida
// a partir de System32.
func reportFatal(msg string) {
	title, err := syscall.UTF16PtrFromString("App Deck — não foi possível iniciar")
	if err != nil {
		return
	}
	text, err := syscall.UTF16PtrFromString(msg)
	if err != nil {
		return
	}

	user32 := syscall.NewLazyDLL("user32.dll")
	messageBox := user32.NewProc("MessageBoxW")
	_, _, _ = messageBox.Call(
		0,
		uintptr(unsafe.Pointer(text)),
		uintptr(unsafe.Pointer(title)),
		mbOK|mbIconError|mbSystemModal,
	)
}
