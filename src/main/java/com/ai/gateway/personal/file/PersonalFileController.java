package com.ai.gateway.personal.file;
import com.ai.gateway.authentication.*; import com.ai.gateway.personal.file.entity.PersonalFile; import com.ai.gateway.personal.file.repository.PersonalFileRepository;
import jakarta.servlet.http.HttpServletRequest; import lombok.RequiredArgsConstructor; import org.springframework.beans.factory.annotation.Value; import org.springframework.core.io.*; import org.springframework.http.*; import org.springframework.security.access.AccessDeniedException; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
import java.io.IOException; import java.nio.file.*; import java.security.MessageDigest; import java.util.*;
@RestController @RequestMapping("/api/personal/files") @RequiredArgsConstructor
public class PersonalFileController {
 private static final long MAX_SIZE=15_000_000L; private final PersonalFileRepository repository;
 @Value("${airouter.personal.files.storage-path:./data/personal-files}") private String storagePath;
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public FileResponse upload(HttpServletRequest request,@RequestPart("file") MultipartFile file)throws IOException{
  UUID accountId=context(request).getPersonalAccountId(); if(file==null||file.isEmpty())throw new IllegalArgumentException("File is empty.");
  if(file.getSize()>MAX_SIZE)throw new IllegalArgumentException("File exceeds the 15 MB limit.");
  String original=Optional.ofNullable(file.getOriginalFilename()).orElse("upload").replaceAll("[\\/\\r\\n]","_").trim(); if(original.isBlank())original="upload"; if(original.length()>255)original=original.substring(0,255);
  String storageName=UUID.randomUUID()+".bin"; Path root=Paths.get(storagePath).toAbsolutePath().normalize(); Files.createDirectories(root); Path target=root.resolve(storageName); file.transferTo(target);
  String sha256; try{sha256=sha256(target);}catch(Exception ex){Files.deleteIfExists(target);throw new IOException("Unable to hash uploaded file.",ex);}
  PersonalFile saved;
  try{
   saved=repository.saveAndFlush(PersonalFile.builder()
    .personalAccountId(accountId)
    .originalName(original)
    .storageName(storageName)
    .contentType(Optional.ofNullable(file.getContentType()).filter(v->!v.isBlank()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE))
    .size(file.getSize())
    .sha256(sha256)
    .build());
  }catch(RuntimeException ex){
   Files.deleteIfExists(target);
   throw ex;
  }
  return response(saved);
 }
 @GetMapping("/{fileId}/content")
 public ResponseEntity<Resource> content(HttpServletRequest request,@PathVariable UUID fileId){
  UUID accountId=context(request).getPersonalAccountId(); PersonalFile file=repository.findByIdAndPersonalAccountId(fileId,accountId).orElseThrow(()->new AccessDeniedException("File not found."));
  Path root=Paths.get(storagePath).toAbsolutePath().normalize(),target=root.resolve(file.getStorageName()).normalize(); if(!target.startsWith(root)||!Files.exists(target))throw new AccessDeniedException("File content is unavailable.");
  Resource resource=new FileSystemResource(target); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.getContentType())).contentLength(file.getSize()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(file.getOriginalName()).build().toString()).body(resource);
 }
 private FileResponse response(PersonalFile f){return new FileResponse(f.getId(),f.getOriginalName(),f.getContentType(),f.getSize(),f.getSha256(),"/api/personal/files/"+f.getId()+"/content",f.getCreatedAt());}
 private AuthenticationContext context(HttpServletRequest request){AuthenticationContext c=(AuthenticationContext)request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null||c.getAuthenticationType()!=AuthenticationType.PERSONAL_SESSION)throw new AccessDeniedException("Personal session authentication is required.");return c;}
 private static String sha256(Path path)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder s=new StringBuilder(64);for(byte b:d.digest())s.append(String.format("%02x",b));return s.toString();}
 public record FileResponse(UUID id,String name,String contentType,long size,String sha256,String url,java.time.LocalDateTime createdAt){}
}
