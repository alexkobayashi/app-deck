// Package netinfo descobre os endereços IPv4 da máquina na rede local.
//
// É usado para dizer ao usuário em que endereço configurar o app e, na
// fase da bandeja, para montar o payload do QR de pareamento.
package netinfo

import (
	"net"
	"sort"
	"strings"
)

// virtualMarkers são pedaços de nome de interface que denunciam adaptadores
// virtuais. O protótipo devolvia o primeiro IPv4 não-loopback que
// encontrasse, o que numa máquina com WSL ou Docker instalado costuma ser
// justamente um endereço que o celular não alcança.
var virtualMarkers = []string{
	"vethernet",
	"virtualbox",
	"vmware",
	"hyper-v",
	"docker",
	"wsl",
	"loopback",
	"bluetooth",
	"tap-",
	"tun",
}

// LocalIPv4s devolve os IPv4 utilizáveis, do mais provável de ser a LAN
// doméstica para o menos provável.
func LocalIPv4s() []net.IP {
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil
	}

	var out []net.IP
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		if isVirtual(iface.Name) {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			ipnet, ok := addr.(*net.IPNet)
			if !ok {
				continue
			}
			ip := ipnet.IP.To4()
			if ip == nil || ip.IsLoopback() || ip.IsLinkLocalUnicast() {
				continue
			}
			out = append(out, ip)
		}
	}

	sort.SliceStable(out, func(i, j int) bool {
		return rank(out[i]) < rank(out[j])
	})
	return out
}

// PreferredIPv4 é o endereço mais provável de funcionar para o celular,
// ou nil se nenhum for encontrado.
func PreferredIPv4() net.IP {
	ips := LocalIPv4s()
	if len(ips) == 0 {
		return nil
	}
	return ips[0]
}

func isVirtual(name string) bool {
	lower := strings.ToLower(name)
	for _, m := range virtualMarkers {
		if strings.Contains(lower, m) {
			return true
		}
	}
	return false
}

// rank ordena as faixas privadas mais comuns em rede doméstica primeiro.
func rank(ip net.IP) int {
	switch {
	case ip[0] == 192 && ip[1] == 168:
		return 0
	case ip[0] == 10:
		return 1
	case ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31:
		return 2
	case ip.IsPrivate():
		return 3
	default:
		return 4
	}
}
