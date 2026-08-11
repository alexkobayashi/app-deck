//go:build !windows

package autostart

// unsupported existe para que cmd/deck-server compile e seja verificado no
// runner Linux do CI.
type unsupported struct{}

// New devolve um gerenciador que recusa qualquer operação.
func New(string, ...string) Manager { return unsupported{} }

func (unsupported) Enabled() (bool, error) { return false, ErrUnsupported }
func (unsupported) Enable() error          { return ErrUnsupported }
func (unsupported) Disable() error         { return ErrUnsupported }
