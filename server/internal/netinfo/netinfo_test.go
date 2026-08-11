package netinfo

import (
	"net"
	"sort"
	"testing"
)

func TestIsVirtual(t *testing.T) {
	virtual := []string{"vEthernet (WSL)", "VMware Network Adapter VMnet8", "Loopback Pseudo-Interface 1", "docker0", "VirtualBox Host-Only Network"}
	real := []string{"Wi-Fi", "Ethernet", "eth0", "wlan0", "Conexão Local"}

	for _, name := range virtual {
		if !isVirtual(name) {
			t.Errorf("isVirtual(%q) = false, quero true", name)
		}
	}
	for _, name := range real {
		if isVirtual(name) {
			t.Errorf("isVirtual(%q) = true, quero false", name)
		}
	}
}

// O protótipo devolvia o primeiro IPv4 que encontrava; numa máquina com WSL
// ou Docker isso costuma ser justamente o endereço que o celular não
// alcança. A ordenação por faixa privada corrige isso.
func TestRankPrefersHomeNetworkRanges(t *testing.T) {
	ips := []net.IP{
		net.ParseIP("100.64.0.1").To4(),
		net.ParseIP("172.17.0.1").To4(),
		net.ParseIP("10.0.0.5").To4(),
		net.ParseIP("192.168.0.10").To4(),
	}
	sort.SliceStable(ips, func(i, j int) bool { return rank(ips[i]) < rank(ips[j]) })

	want := []string{"192.168.0.10", "10.0.0.5", "172.17.0.1", "100.64.0.1"}
	for i, w := range want {
		if ips[i].String() != w {
			t.Fatalf("posição %d = %s, quero %s (ordem completa: %v)", i, ips[i], w, ips)
		}
	}
}

// Sem asserções sobre o resultado: o ambiente de CI pode não ter nenhuma
// interface elegível. O que importa é não entrar em pânico e nunca devolver
// loopback.
func TestLocalIPv4sIsSafe(t *testing.T) {
	for _, ip := range LocalIPv4s() {
		if ip.To4() == nil {
			t.Errorf("%v não é IPv4", ip)
		}
		if ip.IsLoopback() {
			t.Errorf("%v é loopback e não deveria ser listado", ip)
		}
	}
	if ip := PreferredIPv4(); ip != nil && ip.To4() == nil {
		t.Errorf("PreferredIPv4 = %v, não é IPv4", ip)
	}
}
