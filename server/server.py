import socket
import os
from datetime import datetime
import threading
from queue import Queue
from PIL import Image, ImageTk
import tkinter as tk

# --- Configurações ---
HOST = '0.0.0.0'  # Ouve em todas as interfaces de rede disponíveis
PORT = 5001
SAVE_DIR = "data"

# Fila para comunicação segura entre a thread de rede e a thread da GUI
image_queue = Queue()

def setup_directories():
    """Garante que o diretório base para salvar imagens exista."""
    if not os.path.exists(SAVE_DIR):
        os.makedirs(SAVE_DIR)
        print(f"Diretório '{SAVE_DIR}' criado.")

def handle_client_connection(conn, addr):
    """Lida com uma única conexão de cliente para receber uma imagem."""
    print(f"[REDE] Conectado por {addr}")
    try:
        # Passo 1: Receber o tamanho da imagem (inteiro de 4 bytes)
        # O cliente Android deve enviar isso primeiro!
        img_size_bytes = conn.recv(4)
        if not img_size_bytes:
            return # Conexão fechada prematuramente
            
        # Converte os 4 bytes para um inteiro (big-endian)
        img_size = int.from_bytes(img_size_bytes, 'big')
        print(f"[REDE] Recebendo imagem de tamanho: {img_size} bytes")

        # Passo 2: Receber os dados da imagem em partes
        img_data = b''
        while len(img_data) < img_size:
            # Pede o restante dos dados ou um buffer de 4096, o que for menor
            chunk = conn.recv(min(img_size - len(img_data), 4096))
            if not chunk:
                # Conexão interrompida
                raise ConnectionError("Conexão perdida durante o recebimento da imagem.")
            img_data += chunk
        
        print("[REDE] Imagem recebida com sucesso.")

        # Passo 3: Salvar a imagem
        now = datetime.now()
        date_dir = now.strftime("%Y-%m-%d")
        full_dir_path = os.path.join(SAVE_DIR, date_dir)

        if not os.path.exists(full_dir_path):
            os.makedirs(full_dir_path)

        filename = now.strftime("%H%M%S") + ".jpg"
        filepath = os.path.join(full_dir_path, filename)

        with open(filepath, 'wb') as img_file:
            img_file.write(img_data)
        
        print(f"[ARQUIVO] Imagem salva em: {filepath}")

        # Passo 4: Colocar o caminho da imagem na fila para a GUI atualizar
        image_queue.put(filepath)

    except Exception as e:
        print(f"[ERRO] Erro ao lidar com o cliente {addr}: {e}")
    finally:
        print(f"[REDE] Fechando conexão com {addr}")
        conn.close()

def start_server():
    """Inicia o servidor de socket para ouvir por conexões."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind((HOST, PORT))
        s.listen()
        print(f"[SERVIDOR] Ouvindo em {HOST}:{PORT}...")
        while True:
            conn, addr = s.accept()
            # Inicia uma nova thread para cada cliente para não bloquear o servidor
            client_thread = threading.Thread(target=handle_client_connection, args=(conn, addr))
            client_thread.start()

class ImageDisplayApp:
    """Classe que gerencia a janela da GUI com Tkinter."""
    def __init__(self, root):
        self.root = root
        self.root.title("Visualizador de Imagem do Servidor")
        self.root.geometry("800x600")

        self.label = tk.Label(root, text="Aguardando foto...", font=("Helvetica", 16))
        self.label.pack(expand=True)
        
        self.photo_image = None # Para evitar que a imagem seja coletada pelo garbage collector
        
        # Inicia a verificação da fila por novas imagens
        self.check_queue()

    def check_queue(self):
        """Verifica a fila por novos caminhos de imagem a cada 200ms."""
        try:
            filepath = image_queue.get_nowait()
            self.update_image(filepath)
        except Exception: # A fila estava vazia
            pass
        finally:
            # Agenda a próxima verificação
            self.root.after(200, self.check_queue)

    def update_image(self, filepath):
        """Atualiza a imagem exibida na janela."""
        try:
            print(f"[GUI] Atualizando imagem com: {filepath}")
            img = Image.open(filepath)
            
            # Redimensiona a imagem para caber na janela, mantendo a proporção
            img.thumbnail((800, 600))
            
            self.photo_image = ImageTk.PhotoImage(img)
            
            self.label.config(image=self.photo_image, text="") # Remove o texto "Aguardando"
            self.label.image = self.photo_image # Mantém referência
            self.root.title(f"Última Foto: {os.path.basename(filepath)}")

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