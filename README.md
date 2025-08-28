# Atividade 2 - Foto via Botão (Android -> Servidor Python por Sockets)

Este projeto implementa uma comunicação cliente-servidor onde:  
- Um **app Android** (em Kotlin) captura uma foto com a câmera, comprime em JPEG (~80%, máx. 1280px de largura) e envia via **socket TCP**.  
- Um **servidor Python** recebe a imagem, salva em disco com **timestamp** e exibe a última foto recebida em uma janela gráfica (Tkinter).  

---

## Como executar

### 1. Servidor (Python)
1. Instale as dependências:
   ```bash
   pip install pillow
   ```

   (Tkinter já vem com Python em muitas distribuições, mas pode ser necessário instalar python3-tk no Linux).

2. Execute o servidor:
    ```bash
   python server.py
   ```
   - Ele cria a pasta data/AAAA-MM-DD/ para salvar as imagens recebidas.
   - A janela inicial mostra "Aguardando foto…".

### 2. Aplicativo (Android)
1. Abra o projeto no Android Studio.
2. Conecte um dispositivo físico (ou use um emulador com câmera).
3. Execute o app.
4. Na tela inicial:
    - Digite o IP do servidor (mesma rede local do PC).
    - Toque no botão/área da câmera para tirar a foto.
    - O app envia a imagem automaticamente para o servidor na porta 5001.

## Demonstração
Fluxo sugerido para teste:
1. Inicie o servidor Python → janela exibe "Aguardando foto…".
2. No app Android: insira IP e toque em "Tirar e Enviar".
3. O servidor salva a foto em data/AAAA-MM-DD/HHMMSS.jpg e a janela é atualizada com a imagem.
4. Tire outra foto → a janela é atualizada novamente.

## Capturas de tela
