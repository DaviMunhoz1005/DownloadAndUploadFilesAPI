package br.com.api.service;

import br.com.api.config.FileStorageProperties;
import br.com.api.dto.FileDto;
import br.com.api.entities.File_;
import br.com.api.exception.BadRequestException;
import br.com.api.repository.FileRepository;

import jakarta.transaction.Transactional;

import org.apache.commons.io.FilenameUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.Objects;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final FileStorageProperties fileStorageProperties;
    private final Path fileStorageLocation;

    /*

    TODO - atualizar método de updateFile para que ele:
           certifique as versões anteriores dos arquivos;
           veja a versão do arquivo que está atualizando;
           modifique o nome do arquivo físico e no banco de dados para ficar no padrão nome_v*.*;
           atualize o arquivo físico;

    */

    public FileService() {

        this.fileRepository = null;
        this.fileStorageProperties = null;
        this.fileStorageLocation = null;
    }

    @Autowired
    public FileService(FileRepository fileRepository, FileStorageProperties fileStorageProperties) {

        this.fileRepository = fileRepository;
        this.fileStorageProperties = fileStorageProperties;
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDirectory())
                .toAbsolutePath()
                .normalize();
    }

    public List<File_> listAllFiles() {

        return fileRepository.findAll();
    }

    public List<File_> listFilesByName(String name) {

        return fileRepository.findByName(name);
    }

    @Transactional
    public String addNewFile(MultipartFile multipartFile, FileDto fileDto) throws IOException {

        String originalFileName = StringUtils.cleanPath(
                Objects.requireNonNull(multipartFile.getOriginalFilename())
        );

        String baseName = FilenameUtils.getBaseName(originalFileName);
        String extension = FilenameUtils.getExtension(originalFileName);

        boolean nameAlreadyExisting = nameAlreadyExisting(baseName);

        if(nameAlreadyExisting) {

            File_ fileToSave = File_.builder()
                    .name(baseName)
                    .extension(extension)
                    .version(1)
                    .validity(fileDto.validity())
                    .build();

            Path filePathStorage = fileStorageProperties.getFileStorageLocation();
            Files.createDirectories(filePathStorage);
            Path destinationFile = filePathStorage.resolve
                            (Paths.get(fileToSave.getName() + "." + fileToSave.getExtension()))
                    .normalize()
                    .toAbsolutePath();

            multipartFile.transferTo(destinationFile);
            fileRepository.save(fileToSave);

            return fileToSave.getName();
        } else {

            throw new BadRequestException("This file name already exists!");
        }
    }

    public Boolean nameAlreadyExisting(String name) {

        List<File_> fileListWithThisName = fileRepository.findByName(name);
        return fileListWithThisName.isEmpty();
    }

    @Transactional
    public File_ updateFile(MultipartFile multipartFile, FileDto fileDto) throws IOException {

        String originalFileName = StringUtils.cleanPath(
                Objects.requireNonNull(multipartFile.getOriginalFilename())
        );

        String baseName = FilenameUtils.getBaseName(originalFileName);

        if (originalFileName.isEmpty()) {

            throw new IllegalArgumentException("The 'name' field cannot be empty");
        }

        List<File_> listFiles = listFilesByName(baseName);

        if (listFiles.isEmpty()) {

            throw new IllegalArgumentException("No files found with the specified name: " + baseName);
        }

        File_ previousFile = listFiles.get(listFiles.size() - 1);

        Files.createDirectories(fileStorageLocation);
        Path destinationFile = fileStorageLocation.resolve(originalFileName).normalize().toAbsolutePath();

        multipartFile.transferTo(destinationFile);

        File_ fileToSave = File_.builder()
                .name(previousFile.getName())
                .extension(previousFile.getExtension())
                .version(previousFile.getVersion() + 1)
                .validity(fileDto.validity() != null ? fileDto.validity() : previousFile.getValidity())
                .build();

        return fileRepository.save(fileToSave);
    }

    @Transactional
    public Resource downloadFile(String fileName) throws MalformedURLException {

        Path filePath = fileStorageLocation.resolve(fileName).normalize();
        return new UrlResource(filePath.toUri());
    }

    @Transactional
    public void deleteFileById(Long id) {

        File_ file = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException
                        (("File not found with the specified ID:" + id)));

        String originalFileName = file.getName() + "." + file.getExtension();

        Path filePath = fileStorageLocation.resolve(originalFileName).normalize().toAbsolutePath();
        try {

            Files.deleteIfExists(filePath);
        } catch (IOException ioexception) {

            throw new BadRequestException("Error deleting file from file system: " + file.getName());
        }

        fileRepository.deleteById(id);
    }

    @Transactional
    public void deleteFileByName(String name) {

        List<File_> listFiles = listFilesByName(name);

        if (listFiles.isEmpty()) {

            throw new BadRequestException("No files found with the specified name: " + name);
        }

        for (File_ file : listFiles) {

            String originalFileName = file.getName() + "." + file.getExtension();
            Path filePath = fileStorageLocation.resolve(originalFileName).normalize().toAbsolutePath();

            try {

                Files.deleteIfExists(filePath);
            } catch (IOException ioexception) {

                throw new BadRequestException("Error deleting file from file system: " + originalFileName);
            }

            fileRepository.deleteById(file.getId());
        }
    }
}
