//go:build !windows

package main

// configureConsole não faz nada fora do Windows: terminais Unix já usam
// UTF-8.
func configureConsole() {}
