import socket #cria comunicação TCP
import os  #manipular diretorios e arquivos
from datetime import datetime #gera nome timestamp) para salvar cada foto
import threading #roda rede em paraelo sem travar GUI
from queue import Queue #fila segura para trocar mensagens entre threads
from PIL import Image, ImageTk #abri imagens e converter para exibir no Tkinter
import tkinter as tk #cria a interface grafica

HOST = '0.0.0.0'  # aceita conexoes em qualquer IP
PORT = 5002 
SAVE_DIR = "data" #onde salvar as fotos

# fila usada para enviar o caminho da nova imagem para a GUI
image_queue = Queue()

#Se a pasta data/ nao existir, cria ela
def setup_directories():
    """Garante que o diretório base para salvar imagens exista."""
    if not os.path.exists(SAVE_DIR):
        os.makedirs(SAVE_DIR)
        print(f"Diretório '{SAVE_DIR}' criado.")

def handle_client_connection(conn, addr):
    """Lida com uma única conexão de cliente para receber uma imagem."""
    print(f"[REDE] Conectado por {addr}") #printa ip/porta do cliente
    try:        
        img_size_bytes = conn.recv(4) #recebe os 4 bytes iniciais que indicam o tamanho da imagem'
        #se nao recebeu nada
        if not img_size_bytes:
            return # Conexão fechada prematuramente
            
        # Converte os 4 bytes para inteiro (big-endian)
        img_size = int.from_bytes(img_size_bytes, 'big')
        print(f"[REDE] Recebendo imagem de tamanho: {img_size} bytes")

        # Passo 2: Receber os dados da imagem em partes de ate 4096 bytes
        img_data = b'' #recebe os dados da imagem aqui, acumulando os pedaços

        while len(img_data) < img_size: #repete ate chegar no tamanho esperado
            
            chunk = conn.recv(min(img_size - len(img_data), 4096)) #le ate n bytes de conexao
            #min(img_size - len(img_data), 4096) para decidir quantos bytes pedir:
            # img_size - len(img_data) = quanto falta receber da imagem.
            #4096 = limite do "buffer" de recepção.
            if not chunk:
                #caso chunk venha vazio b''), significa que o cliente fechou a conexao d
                #forma inesperada. Nesse caso, interrompe o recebimento.
                raise ConnectionError("Conexão perdida durante o recebimento da imagem.")
            img_data += chunk #vai acumulando os bytes recebidos
        
        #quando esse lopp terminar, img_data tera a imagem completa (img_size bytes)
        print("[REDE] Imagem recebida com sucesso.")

        #cria uam subpasta com a data atual (tipo 2023-08-27)
        now = datetime.now()
        date_dir = now.strftime("%Y-%m-%d") 
        full_dir_path = os.path.join(SAVE_DIR, date_dir) # junta o caminho base com o nome da pasta do dia
        # exemplo:"imagens/2025-08-27"

        #verifica se a pasta do dia existe, se nao, cria ela
        if not os.path.exists(full_dir_path):
            os.makedirs(full_dir_path)

        filename = now.strftime("%H%M%S") + ".jpg" #cria o nome do arquivo da foto usando a hora exata (hora, minutos, segundo)
        filepath = os.path.join(full_dir_path, filename) #monta o caminho completo ate a foto (nome da pasta, pasta data, nome arquivo hora)

        #abre o arquivo em modo escrita binaria, escreve os dados da imagem no arquivo
        with open(filepath, 'wb') as img_file:
            img_file.write(img_data)
        
        #mostra a confirmação do salvamento com o caminho completo
        print(f"[ARQUIVO] Imagem salva em: {filepath}")

        # Coloca o caminho da imagem na fila para a GUI atualizar
        image_queue.put(filepath)

    except Exception as e: #pega qualquer excessao que ocorrer dentro do try de cima e guarda a mensagem do erro no "e
        print(f"[ERRO] Erro ao lidar com o cliente {addr}: {e}")
        #addr: endereço do cliente que causou o erro
        #e: descrição do erro
    finally:
        print(f"[REDE] Fechando conexão com {addr}")
        conn.close() #fecha o socket de comuniação com o cliente

def start_server():
    """Inicia o servidor de socket para ouvir por conexões."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        #Cria um socket TCP (SOCK_STREAM) usando IPv4 (AF_INET)
        # esse socket é o canal de comunicação com os clientes
        #with... as s: Usa context manager, garantindo que o socket seja fechado automaticamente quando o bloco terminar

        s.bind((HOST, PORT)) #associa o socket ao ip e porta definidos

        s.listen() #coloca o socket em modo de escuta, aguardando conexoes
        print(f"[SERVIDOR] Ouvindo em {HOST}:{PORT}...") #printa que servidor esta ativo
        
        while True:
            conn, addr = s.accept() #s.accept() bloqueia até que um cliente se conecte.
            #Retorna dois valores:
            #conn → socket da conexão específica com o cliente.
            #addr → endereço IP e porta do cliente.

            # cria uma nova thread para lidar com este client usando a função handle_client_connection
            #passando o conn e addr como argumentos
            client_thread = threading.Thread(target=handle_client_connection, args=(conn, addr))
            client_thread.start() #inica a thread em pararelo, permitindo que o servidor volte imediamente a aceitar novas conexoes

class ImageDisplayApp:
    """Classe que gerencia a janela da GUI com Tkinter."""
    def __init__(self, root):
        #root é o objeto Tk() do tkinter, que representa a janela principal
        self.root = root
        self.root.title("Visualizador de Imagem do Servidor") #titulo da barra de ttulo da janela
        self.root.geometry("800x600") #tamanho da janela (largura x altura)

        #cria um label que inicialmente mostra o texto "Aguardando foto..."
        self.label = tk.Label(root, text="Aguardando foto...", font=("Helvetica", 16))
        self.label.pack(expand=True) #centralizado e expande para ocupar o espaço disponivel
        
        self.photo_image = None # Para evitar que a imagem seja coletada pelo garbage collector
        
        # Inicia a verificação da fila por novas imagens
        #garante que a imagem seja exibida sem travar a janela
        self.check_queue()

    def check_queue(self):
        """Verifica a fila por novos caminhos de imagem a cada 200ms."""
        try:
            filepath = image_queue.get_nowait() #tenta pegar um item da fila sem bloquear
            self.update_image(filepath) #se conseguiu, atualiza a imagem exibida no label
        except Exception: # A fila estava vazia, gera uma excessao
            pass #ignora
        finally:
            # Agenda a próxima verificação
            self.root.after(200, self.check_queue) #agenda o metodo em 200ms

    def update_image(self, filepath):
        """Atualiza a imagem exibida na janela."""
        try:
            print(f"[GUI] Atualizando imagem com: {filepath}")
            #filepath = caminho completo da imagm
            img = Image.open(filepath) #abre a imagem e retorna um objeto
            
            # Redimensiona a imagem para caber na janela, mantendo a proporção
            img.thumbnail((800, 600))
            
            self.photo_image = ImageTk.PhotoImage(img) #converte para photoimage
            
            self.label.config(image=self.photo_image, text="") # Remove o texto "Aguardando"
            self.label.image = self.photo_image # Mantém referência
            self.root.title(f"Última Foto: {os.path.basename(filepath)}") #Atualiza o título da janela com o nome do arquivo da foto(apenas o nome do arquivo).

        except Exception as e:
            error_message = f"Erro ao exibir imagem:\n{filepath}\n{e}"
            self.label.config(text=error_message, image='')
            print(f"[GUI] {error_message}")


if __name__ == '__main__':
    # 1. Garante que os diretórios existem
    setup_directories()

    # 2. Inicia o servidor de socket em uma thread separada
    server_thread = threading.Thread(target=start_server, daemon=True)
    server_thread.start()

    # 3. Inicia a aplicação da GUI na thread principal
    root = tk.Tk()
    app = ImageDisplayApp(root)
    root.mainloop()