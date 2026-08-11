//go:build !windows

package main

// reportFatal não faz nada fora do Windows: lá o stderr sempre existe.
func reportFatal(string) {}
