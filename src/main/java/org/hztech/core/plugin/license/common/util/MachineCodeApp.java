package org.hztech.core.plugin.license.common.util;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

/** 桌面 GUI + 服务器 CLI，离线获取 License 机器码。 */
public final class MachineCodeApp {

    private MachineCodeApp() {
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--help") || Arrays.asList(args).contains("-h")) {
            System.out.println("HzTech Machine Code 1.0.0");
            System.out.println("  --cli       只输出机器码（适合自动化/服务器）");
            System.out.println("  --legacy    输出原来的 V1 CPU ID + UUID 机器码");
            System.out.println("  --help      显示帮助");
            return;
        }

        if (Arrays.asList(args).contains("--legacy")) {
            try {
                System.out.println(GetMachineUtil.getLegacyMachineKey());
            } catch (Throwable e) {
                System.err.println("生成旧版机器码失败：" + conciseError(e));
                System.exit(1);
            }
            return;
        }

        if (args.length > 0 && !(args.length == 1 && "--cli".equals(args[0]))) {
            System.err.println("不支持的参数：" + Arrays.toString(args) + "，可使用 --help 查看用法");
            System.exit(2);
            return;
        }

        if (args.length == 1 || GraphicsEnvironment.isHeadless()) {
            try {
                System.out.println(GetMachineUtil.getMachineKey());
            } catch (Throwable e) {
                System.err.println("生成机器码失败：" + conciseError(e));
                System.exit(1);
            }
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 系统外观不可用时使用 Swing 默认外观。
        }
        SwingUtilities.invokeLater(MachineCodeApp::createWindow);
    }

    private static void createWindow() {
        JFrame frame = new JFrame("HzTech 机器码工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(620, 290));

        JPanel content = new JPanel(new BorderLayout(0, 15));
        content.setBorder(BorderFactory.createEmptyBorder(22, 22, 18, 22));

        JLabel title = new JLabel("设备机器码（License V2）");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        content.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        JTextArea key = new JTextArea("正在读取硬件信息……");
        key.setEditable(false);
        key.setLineWrap(true);
        key.setWrapStyleWord(true);
        key.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17));
        key.setRows(3);
        key.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 204, 210)),
                BorderFactory.createEmptyBorder(14, 12, 12, 12)));
        key.setBackground(UIManager.getColor("Panel.background"));
        center.add(key, BorderLayout.CENTER);

        JLabel source = new JLabel("仅读取本机硬件信息，不联网、不上传原始序列号");
        source.setForeground(new Color(94, 100, 108));
        center.add(source, BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);

        JButton copy = new JButton("复制机器码");
        JButton refresh = new JButton("重新读取");
        copy.setEnabled(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.add(refresh);
        buttons.add(copy);
        content.add(buttons, BorderLayout.SOUTH);

        Runnable reload = () -> {
            key.setText("正在读取硬件信息……");
            source.setText("读取中，请稍候");
            copy.setEnabled(false);
            refresh.setEnabled(false);
            new SwingWorker<MachineKeyGenerator.Result, Void>() {
                @Override
                protected MachineKeyGenerator.Result doInBackground() {
                    return GetMachineUtil.getMachineKeyResult();
                }

                @Override
                protected void done() {
                    refresh.setEnabled(true);
                    try {
                        MachineKeyGenerator.Result result = get();
                        key.setText(result.machineKey());
                        source.setText("算法 V" + result.version() + "  ·  标识来源：" + result.source().displayName()
                                + "  ·  信息仅在本机计算");
                        copy.setEnabled(true);
                    } catch (Exception e) {
                        key.setText("机器码生成失败");
                        source.setText(conciseError(e));
                        JOptionPane.showMessageDialog(frame, conciseError(e),
                                "无法读取机器码", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        };

        copy.addActionListener(event -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(key.getText()), null);
                JOptionPane.showMessageDialog(frame, "机器码已复制到剪贴板。", "复制成功",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, conciseError(e), "复制失败",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        refresh.addActionListener(event -> reload.run());

        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        reload.run();
    }

    private static String conciseError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
