
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
import java.util.Scanner;

public class Cliente {

    public static void main(String[] args) {

        String ipServidor = "100.114.78.99";
        int porta = 5000;

        try (
                Socket socket = new Socket(
                        ipServidor,
                        porta
                );

                BufferedReader entrada =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                BufferedWriter saida =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        socket.getOutputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                Scanner teclado = new Scanner(System.in)
        ) {

          // Aguarda a resposta inicial do servidor.
            String status = entrada.readLine();

            // Verifica se o servidor encerrou a conexão.
            if (status == null) {

                System.out.println(
                        "O servidor encerrou a conexao."
                );

                return;
            }

            // Verifica se o servidor recusou a conexão.
            if (status.equals("FULL")) {

                System.out.println(
                        "Conexao recusada: servidor cheio."
                );

                return;
            }

            // Confirma que a conexão foi aceita.
            if (!status.equals("CONNECTED")) {

                System.out.println(
                        "Resposta inicial inesperada do servidor: "
                                + status
                );

                return;
            }

            System.out.println("Conectado ao servidor!");

            boolean conectado = true;

            while (conectado) {

                System.out.println();
                System.out.println("===== MENU =====");
                System.out.println("1 - Adicao");
                System.out.println("2 - Subtracao");
                System.out.println("3 - Multiplicacao");
                System.out.println("4 - Solicitar imagem");
                System.out.println("0 - Desconectar");

                System.out.print("Escolha uma opcao: ");

                String comando = teclado.nextLine().trim();

                String mensagem;

                switch (comando) {

                    case "1":
                    case "2":
                    case "3":

                        System.out.print("Digite o primeiro numero: ");
                        String numero1 = teclado.nextLine().trim();

                        System.out.print("Digite o segundo numero: ");
                        String numero2 = teclado.nextLine().trim();

                        mensagem = comando
                                + "|" + numero1
                                + "|" + numero2;

                        break;

                    case "0":

                        mensagem = "0";
                        break;

                    case "4":

                        mensagem = "4";
                        break;

                    default:

                        System.out.println(
                                "Opcao invalida. Tente novamente."
                        );

                        continue;
                }

                saida.write(mensagem);
                saida.newLine();
                saida.flush();

                String resposta = entrada.readLine();

                if (resposta == null) {

                    System.out.println(
                            "O servidor encerrou a conexao."
                    );

                    break;
                }

                if (resposta.equals("FULL")) {

                    System.out.println(
                            "Conexao recusada: servidor cheio."
                    );

                    break;
                }

                if (resposta.equals("BYE")) {

                    System.out.println(
                            "Desconectado do servidor."
                    );

                    break;
                }

                if (resposta.startsWith("IMAGE|")) {

                    receberImagem(
                            entrada,
                            resposta
                    );

                } else if (resposta.startsWith("RESULT|")) {

                    String resultado = resposta.substring(
                            "RESULT|".length()
                    );

                    System.out.println(
                            "Resultado recebido: " + resultado
                    );

                } else if (resposta.startsWith("ERROR|")) {

                    String erro = resposta.substring(
                            "ERROR|".length()
                    );

                    System.out.println(
                            "Erro: " + erro
                    );

                } else {

                    System.out.println(
                            "Resposta do servidor: " + resposta
                    );
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Erro de comunicacao: " + e.getMessage()
            );
        }
    }

    private static void receberImagem(
            BufferedReader entrada,
            String cabecalho
    ) throws IOException {

        String[] partes = cabecalho.split("\\|", -1);

        if (partes.length != 2) {

            System.out.println(
                    "Cabecalho de imagem invalido."
            );

            return;
        }

        int tamanhoEsperado;

        try {

            tamanhoEsperado = Integer.parseInt(partes[1]);

        } catch (NumberFormatException e) {

            System.out.println(
                    "Tamanho da imagem invalido."
            );

            return;
        }

        if (tamanhoEsperado < 0) {

            System.out.println(
                    "Tamanho da imagem invalido."
            );

            return;
        }

        // Le a linha contendo o conteudo Base64.

        String conteudoBase64 = entrada.readLine();

        if (conteudoBase64 == null) {

            throw new IOException(
                    "Conexao encerrada durante o recebimento da imagem."
            );
        }

        // Confere se o tamanho recebido corresponde
        // ao informado no cabecalho.

        if (conteudoBase64.length() != tamanhoEsperado) {

            System.out.println(
                    "Erro: tamanho do conteudo recebido "
                            + "diferente do esperado."
            );

            return;
        }

        byte[] bytesImagem;

        try {

            bytesImagem = Base64.getDecoder()
                    .decode(conteudoBase64);

        } catch (IllegalArgumentException e) {

            System.out.println(
                    "O conteudo recebido nao e um Base64 valido."
            );

            return;
        }

        // Salva a imagem no diretorio do cliente.

        Path diretorio = Path.of("cliente-java");

        if (!Files.isDirectory(diretorio)) {

            diretorio = Path.of(".");
        }

        Path arquivoDestino = diretorio.resolve(
                "imagem_recebida.png"
        );

        Files.write(
                arquivoDestino,
                bytesImagem
        );

        System.out.println(
                "Imagem recebida e salva com sucesso!"
        );

        System.out.println(
                "Arquivo: "
                        + arquivoDestino.toAbsolutePath()
        );

        System.out.println(
                "Tamanho original: "
                        + bytesImagem.length
                        + " bytes"
        );
    }

}