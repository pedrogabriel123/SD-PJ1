
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Connection {

    private final Socket socket;
    private final BufferedReader entrada;
    private final BufferedWriter saida;

    private final BlockingQueue<String> filaSaida =
            new ArrayBlockingQueue<>(100);

    private final Runnable aoEncerrar;

    private Thread threadLeitura;
    private Thread threadEscrita;

    private volatile boolean encerrada = false;
    private volatile boolean encerramentoGracioso = false;

    public Connection(
            Socket socket,
            Runnable aoEncerrar
    ) throws IOException {

        this.socket = socket;
        this.aoEncerrar = aoEncerrar;

        this.entrada = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        this.saida = new BufferedWriter(
                new OutputStreamWriter(
                        socket.getOutputStream(),
                        StandardCharsets.UTF_8
                )
        );
    }

    public void iniciar() {

        threadEscrita = new Thread(
                this::executarEscrita
        );

        threadLeitura = new Thread(
                this::executarLeitura
        );

        threadEscrita.start();
        threadLeitura.start();
    }

    // =====================================================
    // THREAD DE LEITURA
    // =====================================================

    private void executarLeitura() {

        try {

            String mensagem;

            while (!encerrada
                    && (mensagem = entrada.readLine()) != null) {

                System.out.println(
                        "Mensagem recebida: " + mensagem
                );

                processarMensagem(mensagem);

                if (encerramentoGracioso) {
                    return;
                }
            }

        } catch (IOException e) {

            if (!encerrada) {
                System.out.println(
                        "Erro na leitura: " + e.getMessage()
                );
            }

        } finally {

            if (!encerramentoGracioso) {
                encerrar();
            }
        }
    }

    // =====================================================
    // PROCESSAMENTO DOS COMANDOS
    // =====================================================

    private void processarMensagem(String mensagem) {

        String[] partes = mensagem.split("\\|", -1);

        String comando = partes[0];

        switch (comando) {

            case "0":

                if (partes.length != 1) {
                    enviar("ERROR|Comando 0 invalido");
                    return;
                }

                solicitarEncerramentoGracioso();

                break;

            case "1":
            case "2":
            case "3":

                processarOperacao(partes);

                break;

            case "4":

                if (partes.length != 1) {

                    enviar("ERROR|Comando 4 invalido");

                } else {

                    enviarImagem();
                }

                break;

            default:

                enviar("ERROR|Comando desconhecido");

                break;
        }
    }

    private void processarOperacao(String[] partes) {

        if (partes.length != 3) {

            enviar(
                    "ERROR|Formato invalido. Use comando|numero1|numero2"
            );

            return;
        }

        try {

            double numero1 = Double.parseDouble(partes[1]);
            double numero2 = Double.parseDouble(partes[2]);

            double resultado;

            switch (partes[0]) {

                case "1":

                    resultado = numero1 + numero2;
                    break;

                case "2":

                    resultado = numero1 - numero2;
                    break;

                case "3":

                    resultado = numero1 * numero2;
                    break;

                default:

                    enviar("ERROR|Operacao invalida");
                    return;
            }

            if (!Double.isFinite(resultado)) {

                enviar("ERROR|Resultado fora do limite numerico");

                return;
            }

            enviar("RESULT|" + resultado);

        } catch (NumberFormatException e) {

            enviar("ERROR|Os operandos devem ser numeros validos");

        }
    }


    private void enviarImagem() {

        try {

            // Localiza a imagem, considerando diferentes
            // diretorios de execucao do programa.

            Path[] caminhos = {
                    Path.of("servidor-java", "imagens", "imagem.png"),
                    Path.of("imagens", "imagem.png"),
                    Path.of("..", "imagens", "imagem.png")
            };

            Path arquivoImagem = null;

            for (Path caminho : caminhos) {

                if (Files.isRegularFile(caminho)) {
                    arquivoImagem = caminho;
                    break;
                }
            }

            if (arquivoImagem == null) {

                enviar("ERROR|Arquivo imagem.png nao encontrado");

                return;
            }

            // Le os bytes originais da imagem.

            byte[] bytesImagem = Files.readAllBytes(
                    arquivoImagem
            );

            // Converte os bytes para Base64.

            String imagemBase64 = Base64.getEncoder()
                    .encodeToString(bytesImagem);

            // Envia o cabecalho com o tamanho do conteudo.

            enviar("IMAGE|" + imagemBase64.length());

            // Envia o conteudo Base64 como uma segunda linha.

            enviar(imagemBase64);

            System.out.println(
                    "Imagem enviada. Bytes originais: "
                            + bytesImagem.length
                            + ", caracteres Base64: "
                            + imagemBase64.length()
            );

        } catch (IOException e) {

            enviar("ERROR|Falha ao ler a imagem");

            System.out.println(
                    "Erro ao ler imagem: " + e.getMessage()
            );
        }
    }

    // =====================================================
    // THREAD DE ESCRITA
    // =====================================================

    private void executarEscrita() {

        try {

            while (true) {

                String mensagem = filaSaida.take();

                saida.write(mensagem);
                saida.newLine();
                saida.flush();

                // O BYE somente sera enviado antes do
                // encerramento efetivo do socket.

                if (encerramentoGracioso
                        && mensagem.equals("BYE")) {

                    break;
                }
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } catch (IOException e) {

            if (!encerrada) {
                System.out.println(
                        "Erro na escrita: " + e.getMessage()
                );
            }

        } finally {

            encerrar();
        }
    }

    // =====================================================
    // ENVIO DE MENSAGENS
    // =====================================================

    public void enviar(String mensagem) {

        if (encerrada || encerramentoGracioso) {
            return;
        }

        try {

            filaSaida.put(mensagem);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            encerrar();
        }
    }

    private void solicitarEncerramentoGracioso() {

        if (encerrada || encerramentoGracioso) {
            return;
        }

        encerramentoGracioso = true;

        try {

            filaSaida.put("BYE");

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            encerrar();
        }
    }

    // =====================================================
    // ENCERRAMENTO DA CONEXAO
    // =====================================================

    public void encerrar() {

        synchronized (this) {

            if (encerrada) {
                return;
            }

            encerrada = true;
        }

        try {

            socket.close();

        } catch (IOException e) {

            System.out.println(
                    "Erro ao fechar socket: " + e.getMessage()
            );
        }

        if (threadLeitura != null
                && threadLeitura != Thread.currentThread()) {

            threadLeitura.interrupt();
        }

        if (threadEscrita != null
                && threadEscrita != Thread.currentThread()) {

            threadEscrita.interrupt();
        }

        aoEncerrar.run();
    }
}