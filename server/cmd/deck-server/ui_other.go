//go:build !windows

package main

import "context"

// runUI fora do Windows é sempre modo console: espera um sinal.
//
// Existe para que o binário inteiro compile e seja verificado no runner
// Linux do CI, que é também onde o .exe de release é gerado.
func runUI(ctx context.Context, d appDeps) {
	<-ctx.Done()
	d.onQuit()
}
