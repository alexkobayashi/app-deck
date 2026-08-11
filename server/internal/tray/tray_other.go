//go:build !windows

package tray

import "sync"

// Fora do Windows não há bandeja: Run só bloqueia até Quit, para que o
// fluxo do cmd/deck-server seja idêntico em todas as plataformas e o CI
// Linux continue compilando e testando o binário inteiro.
//
// Isso também evita depender do systray em Linux, onde ele exigiria CGO e
// os headers do GTK.
var (
	quitOnce sync.Once
	quitCh   = make(chan struct{})
)

// Run bloqueia até Quit ser chamada.
func Run(opts Options) {
	<-quitCh
	if opts.OnQuit != nil {
		opts.OnQuit()
	}
}

// Quit libera Run.
func Quit() {
	quitOnce.Do(func() { close(quitCh) })
}
