//go:build !windows

package launcher

import "os/exec"

// applyPlatform não faz nada fora do Windows. Existe para que o pacote
// compile e seja testável no runner Linux do CI.
func applyPlatform(*exec.Cmd) {}
