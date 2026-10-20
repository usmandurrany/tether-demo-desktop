package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Edits our profile (vCard): name, nickname, email, phone, an "about" line and
 * a picture. Saving copies the current profile, so fields this form does not
 * show are kept.
 */
class ProfileDialog(owner: Component, private val session: DemoSession, private val uiScope: CoroutineScope) :
    JDialog(SwingUtilities.getWindowAncestor(owner), "Your profile", ModalityType.APPLICATION_MODAL) {
    private val current = session.chat.myProfile.value ?: Profile()
    private var avatar: Avatar? = current.avatar

    private val fullName = JTextField(current.fullName.orEmpty(), 24)
    private val nickname = JTextField(current.nickname.orEmpty(), 24)
    private val email = JTextField(current.email.orEmpty(), 24)
    private val phone = JTextField(current.phone.orEmpty(), 24)
    private val note = JTextField(current.note.orEmpty(), 24)
    private val preview = AvatarIcon(96)
    private val previewLabel = JLabel(preview)
    private val sizeNote = muted(" ")
    private val save = JButton("Save")

    init {
        preview.avatar = avatar
        preview.name = current.displayName ?: session.account.local.orEmpty()
        refreshSize()

        val pictureButtons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JButton("Choose picture…").apply { addActionListener { choosePicture() } })
            add(JButton("Remove").apply { addActionListener { avatar = null; preview.avatar = null; refreshSize(); previewLabel.repaint() } })
        }
        val picture = JPanel(BorderLayout(12, 4)).apply {
            add(previewLabel, BorderLayout.WEST)
            add(JPanel(BorderLayout()).apply { add(pictureButtons, BorderLayout.NORTH); add(sizeNote, BorderLayout.SOUTH) }, BorderLayout.CENTER)
        }

        val form = JPanel(GridBagLayout())
        var row = 0
        fun add(label: String, field: Component) {
            form.add(JLabel(label), GridBagConstraints().apply { gridx = 0; gridy = row; anchor = GridBagConstraints.LINE_END; insets = Insets(4, 0, 4, 10) })
            form.add(field, GridBagConstraints().apply { gridx = 1; gridy = row++; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; insets = Insets(4, 0, 4, 0) })
        }
        add("Picture", picture)
        add("Full name", fullName)
        add("Nickname", nickname)
        add("Email", email)
        add("Phone", phone)
        add("About", note)

        save.addActionListener { saveProfile() }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Cancel").apply { addActionListener { dispose() } })
            add(save)
        }
        contentPane = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(14, 16, 8, 16)
            add(muted("Contacts see this, and your picture shows next to your name in their lists."), BorderLayout.NORTH)
            add(form, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        rootPane.defaultButton = save
        pack()
        setLocationRelativeTo(owner)
    }

    private fun refreshSize() {
        sizeNote.text = avatar?.let { "JPEG, ${it.data.size / 1024} KB. Pictures are made 192×192 so they fit in one message." } ?: "No picture"
    }

    private fun choosePicture() {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "gif", "bmp") }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val picked = runCatching { Avatars.fromFile(chooser.selectedFile) }.getOrNull()
        if (picked == null) {
            JOptionPane.showMessageDialog(this, "That file isn't an image this app can read.", "Picture", JOptionPane.WARNING_MESSAGE)
            return
        }
        avatar = picked
        preview.avatar = picked
        refreshSize()
        previewLabel.repaint()
    }

    private fun saveProfile() {
        fun JTextField.value() = text.trim().ifEmpty { null }
        val updated = current.copy(
            fullName = fullName.value(),
            nickname = nickname.value(),
            email = email.value(),
            phone = phone.value(),
            note = note.value(),
            avatar = avatar,
        )
        save.isEnabled = false
        uiScope.launch {
            val result = runCatching { session.chat.updateMyProfile(updated) }
            save.isEnabled = true
            result.onSuccess { dispose() }.onFailure {
                JOptionPane.showMessageDialog(this@ProfileDialog, "The server didn't save it: ${it.message}", "Profile", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
}
