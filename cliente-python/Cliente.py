
import socket
import base64
from pathlib import Path


# =====================================================
# CONFIGURACAO DO CLIENTE
# =====================================================

IP_SERVIDOR = "127.0.0.1"
PORTA = 5000


# =====================================================
# RECEBIMENTO DA IMAGEM
# =====================================================

def receber_imagem(entrada, cabecalho):

    partes = cabecalho.split("|")

    if len(partes) != 2:
        print("Cabecalho de imagem invalido.")
        return

    try:
        tamanho_esperado = int(partes[1])

    except ValueError:
        print("Tamanho da imagem invalido.")
        return

    if tamanho_esperado < 0:
        print("Tamanho da imagem invalido.")
        return

    # Le a segunda linha enviada pelo servidor.
    conteudo_base64 = entrada.readline()

    if not conteudo_base64:
        raise ConnectionError(
            "Conexao encerrada durante o recebimento da imagem."
        )

    # Remove a quebra de linha do protocolo.
    conteudo_base64 = conteudo_base64.rstrip("\n")

    # Confere o tamanho informado pelo servidor.
    if len(conteudo_base64) != tamanho_esperado:
        print("Erro: tamanho do conteudo recebido diferente do esperado.")
        return

    try:
        bytes_imagem = base64.b64decode(
            conteudo_base64,
            validate=True
        )

    except (ValueError, base64.binascii.Error):
        print("O conteudo recebido nao e um Base64 valido.")
        return

    # Salva a imagem dentro da pasta do cliente Python.
    diretorio = Path(__file__).resolve().parent

    arquivo_destino = diretorio / "imagem_recebida.png"

    arquivo_destino.write_bytes(bytes_imagem)

    print("\nImagem recebida e salva com sucesso!")

    print("Arquivo:", arquivo_destino)

    print("Tamanho original:", len(bytes_imagem), "bytes")


# =====================================================
# COMUNICACAO COM O SERVIDOR
# =====================================================

def executar_cliente():

    try:

        # Cria a conexao TCP com o servidor Java.
        socket_cliente = socket.create_connection(
            (IP_SERVIDOR, PORTA)
        )

    except OSError as erro:

        print("Erro ao conectar ao servidor:", erro)
        return

    try:

        # Cria os fluxos de leitura e escrita.
        with socket_cliente:

            with socket_cliente.makefile(
                "r",
                encoding="utf-8",
                newline="\n"
            ) as entrada:

                with socket_cliente.makefile(
                    "w",
                    encoding="utf-8",
                    newline="\n"
                ) as saida:

                    # Aguarda a resposta inicial do servidor.
                    status = entrada.readline()

                    if not status:

                        print(
                            "O servidor encerrou a conexao."
                        )

                        return

                    status = status.rstrip("\n")

                    # Verifica se o servidor esta cheio.
                    if status == "FULL":

                        print(
                            "Conexao recusada: servidor cheio."
                        )

                        return

                    # Confirma que a conexao foi aceita.
                    if status != "CONNECTED":

                        print(
                            "Resposta inicial inesperada do servidor:",
                            status
                        )

                        return

                    print("Conectado ao servidor Java!")

                    conectado = True

                    while conectado:

                        print()
                        print("===== MENU =====")
                        print("1 - Adicao")
                        print("2 - Subtracao")
                        print("3 - Multiplicacao")
                        print("4 - Solicitar imagem")
                        print("0 - Desconectar")

                        comando = input(
                            "Escolha uma opcao: "
                        ).strip()

                        # ---------------------------------
                        # OPERACOES MATEMATICAS
                        # ---------------------------------

                        if comando in ("1", "2", "3"):

                            numero1 = input(
                                "Digite o primeiro numero: "
                            ).strip()

                            numero2 = input(
                                "Digite o segundo numero: "
                            ).strip()

                            mensagem = (
                                comando
                                + "|" + numero1
                                + "|" + numero2
                            )

                        # ---------------------------------
                        # DESCONEXAO
                        # ---------------------------------

                        elif comando == "0":

                            mensagem = "0"

                        # ---------------------------------
                        # SOLICITACAO DA IMAGEM
                        # ---------------------------------

                        elif comando == "4":

                            mensagem = "4"

                        else:

                            print(
                                "Opcao invalida. Tente novamente."
                            )

                            continue

                        # Envia a mensagem ao servidor.
                        saida.write(mensagem + "\n")
                        saida.flush()

                        # Aguarda a resposta do servidor.
                        resposta = entrada.readline()

                        if not resposta:

                            print(
                                "O servidor encerrou a conexao."
                            )

                            break

                        resposta = resposta.rstrip("\n")

                        # ---------------------------------
                        # SERVIDOR CHEIO
                        # ---------------------------------

                        if resposta == "FULL":

                            print(
                                "Conexao recusada: servidor cheio."
                            )

                            break

                        # ---------------------------------
                        # DESCONEXAO CONFIRMADA
                        # ---------------------------------

                        elif resposta == "BYE":

                            print(
                                "Desconectado do servidor."
                            )

                            break

                        # ---------------------------------
                        # RECEBIMENTO DA IMAGEM
                        # ---------------------------------

                        elif resposta.startswith("IMAGE|"):

                            receber_imagem(
                                entrada,
                                resposta
                            )

                        # ---------------------------------
                        # RESULTADO MATEMATICO
                        # ---------------------------------

                        elif resposta.startswith("RESULT|"):

                            resultado = resposta[
                                len("RESULT|"):
                            ]

                            print(
                                "Resultado recebido:",
                                resultado
                            )

                        # ---------------------------------
                        # ERRO DO SERVIDOR
                        # ---------------------------------

                        elif resposta.startswith("ERROR|"):

                            erro = resposta[
                                len("ERROR|"):
                            ]

                            print("Erro:", erro)

                        else:

                            print(
                                "Resposta do servidor:",
                                resposta
                            )

    except (OSError, ConnectionError) as erro:

        print("Erro de comunicacao:", erro)


# =====================================================
# PONTO DE ENTRADA DO PROGRAMA
# =====================================================

if __name__ == "__main__":

    executar_cliente()