package main

import (
	"encoding/json"
	"fmt"
	"html/template"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
)

// AppShortcut representa um botão do deck: nome exibido, ícone (emoji) e
// caminho completo do executável no Windows.
type AppShortcut struct {
	Name string `json:"name"`
	Icon string `json:"icon"`
	Path string `json:"path"`
}

// Config é o conteúdo de config.json.
type Config struct {
	Token string        `json:"token"`
	Porta string        `json:"porta"`
	Apps  []AppShortcut `json:"apps"`
}

// PageData é o que a página HTML recebe para se renderizar.
type PageData struct {
	Token string
	Apps  []AppShortcut
}

var config Config

const pageTemplate = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Meu Deck</title>
<style>
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    background: #0f0f10;
    color: #f2f2f0;
    font-family: -apple-system, "Segoe UI", sans-serif;
    margin: 0;
    padding: 32px 20px;
  }
  h1 {
    font-size: 20px;
    font-weight: 500;
    text-align: center;
    margin: 0 0 28px;
    color: #a8a6f6;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 14px;
    max-width: 480px;
    margin: 0 auto;
  }
  button {
    background: #1c1c1f;
    border: 1px solid #2a2a2e;
    border-radius: 18px;
    padding: 22px 8px;
    color: #f2f2f0;
    font-size: 13px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    transition: background 0.15s;
  }
  button:active { background: #2a2a2e; }
  .icon { font-size: 30px; line-height: 1; }
  .status {
    text-align: center;
    margin-top: 22px;
    font-size: 13px;
    color: #8a8a8f;
    min-height: 20px;
  }
</style>
</head>
<body>
<h1>Meu Deck</h1>
<div class="grid">
{{range .Apps}}
  <button onclick="abrir('{{.Name}}')">
    <span class="icon">{{.Icon}}</span>
    {{.Name}}
  </button>
{{end}}
</div>
<div class="status" id="status"></div>
<script>
var token = "{{.Token}}";
function abrir(nome) {
  var status = document.getElementById('status');
  status.textContent = 'Abrindo ' + nome + '...';
  fetch('/abrir?app=' + encodeURIComponent(nome) + '&token=' + encodeURIComponent(token), { method: 'POST' })
    .then(function (res) {
      status.textContent = res.ok ? (nome + ' aberto!') : ('Erro ao abrir ' + nome);
    })
    .catch(function () {
      status.textContent = 'Sem conexão com o servidor';
    });
  setTimeout(function () { status.textContent = ''; }, 2000);
}
</script>
</body>
</html>`

func carregarConfig() {
	data, err := os.ReadFile("config.json")
	if err != nil {
		log.Fatal("Não consegui ler config.json (ele precisa estar na mesma pasta do .exe): ", err)
	}
	if err := json.Unmarshal(data, &config); err != nil {
		log.Fatal("config.json inválido: ", err)
	}
	if config.Porta == "" {
		config.Porta = "5050"
	}
	if config.Token == "" {
		log.Fatal("Defina um \"token\" em config.json antes de rodar o servidor.")
	}
}

// meuIP tenta achar o IP local (da rede Wi-Fi) da máquina, para mostrar
// no console qual endereço usar no celular.
func meuIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "SEU-IP-LOCAL"
	}
	for _, addr := range addrs {
		if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ip4 := ipnet.IP.To4(); ip4 != nil {
				return ip4.String()
			}
		}
	}
	return "SEU-IP-LOCAL"
}

func autorizado(r *http.Request) bool {
	return r.URL.Query().Get("token") == config.Token
}

func main() {
	carregarConfig()
	tmpl := template.Must(template.New("deck").Parse(pageTemplate))

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if !autorizado(r) {
			w.WriteHeader(http.StatusUnauthorized)
			fmt.Fprint(w, "Token inválido. Acesse com ?token=SEU_TOKEN no final da URL.")
			return
		}
		tmpl.Execute(w, PageData{Token: config.Token, Apps: config.Apps})
	})

	http.HandleFunc("/abrir", func(w http.ResponseWriter, r *http.Request) {
		if !autorizado(r) {
			http.Error(w, "não autorizado", http.StatusUnauthorized)
			return
		}
		nome := r.URL.Query().Get("app")
		var caminho string
		for _, a := range config.Apps {
			if a.Name == nome {
				caminho = a.Path
				break
			}
		}
		if caminho == "" {
			http.Error(w, "app não encontrado no config.json", http.StatusNotFound)
			return
		}
		cmd := exec.Command(caminho)
		if err := cmd.Start(); err != nil {
			http.Error(w, "erro ao abrir: "+err.Error(), http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
	})

	ip := meuIP()
	fmt.Println("Servidor rodando!")
	fmt.Printf("No celular (mesma rede Wi-Fi), acesse:\nhttp://%s:%s/?token=%s\n\n", ip, config.Porta, config.Token)
	log.Fatal(http.ListenAndServe(":"+config.Porta, nil))
}
