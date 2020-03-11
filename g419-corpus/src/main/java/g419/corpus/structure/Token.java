package g419.corpus.structure;

import java.util.*;

public class Token extends IdentifiableElement {

  public TokenAttributeIndex attrIdx;

  ArrayList<String> attributes = new ArrayList<>();

  ArrayList<Tag> tags = new ArrayList<>();

  Map<String, String> props = new HashMap<>();

  boolean noSpaceAfter = false;

  public Token(final String orth, final TokenAttributeIndex attrIdx) {
    this(attrIdx);
    final int index = attrIdx.getIndex("orth");
    if (index == -1) {
      throw new Error("TokenAttribute Index does not contain the 'orth' attribute");
    }
    setAttributeValue(index, orth);
  }

  public Token(final String orth, final Tag firstTag, final TokenAttributeIndex attrIdx) {
    this(orth, attrIdx);
    addTag(firstTag);
  }

  public Token(final TokenAttributeIndex attrIdx) {
    this.attrIdx = attrIdx;
    packAtributes(attrIdx.getLength());
  }

  public void clearAttributes() {
    attributes = new ArrayList<>();
  }

  public void removeAttribute(final int attrIdx) {
    attributes.remove(attrIdx);
  }

  public String getAttributeValue(final int index) {
    return attributes.get(index);
  }

  public String getAttributeValue(final String attr) {
    final int index = attrIdx.getIndex(attr);
    return getAttributeValue(index);
  }

  public int getNumAttributes() {
    return attributes.size();
  }

  public Map<String, String> getProps() {
    return props;
  }

  public void setProp(final String name, final String value) {
    props.put(name, value);
  }

  public String getOrth() {
    return attributes.get(attrIdx.getIndex("orth"));
  }

  public void setOrth(final String orth) {
    attributes.set(attrIdx.getIndex("orth"), orth);
  }

  public String getElement(final String key) {
    return attributes.get(attrIdx.getIndex(key));
  }

  public boolean getNoSpaceAfter() {
    return noSpaceAfter;
  }

  public void addTag(final Tag tag) {
    tags.add(tag);
    if (attrIdx.getIndex("base") != -1 && attributes.get(attrIdx.getIndex("base")) == null) {
      setAttributeValue(attrIdx.getIndex("base"), tag.getBase());
    }
    if (attrIdx.getIndex("ctag") != -1 && attributes.get(attrIdx.getIndex("ctag")) == null) {
      setAttributeValue(attrIdx.getIndex("ctag"), tag.getCtag());
    }
  }

  public ArrayList<Tag> getTags() {
    return tags;
  }

  public Set<String> getDisambBases() {
    final Set<String> bases = new HashSet<>();
    for (final Tag tag : tags) {
      if (tag.getDisamb()) {
        bases.add(tag.getBase());
      }
    }
    return bases;
  }

  @Override
  public String toString() {
    return "Token{" +
        "attrIdx=" + attrIdx +
        ", attributes=" + attributes +
        ", tags=" + tags +
        ", props=" + props +
        ", noSpaceAfter=" + noSpaceAfter +
        '}';
  }

  public boolean hasDisambTag() {
    return getDisambTag() != null;
  }

  public Tag getDisambTag() {
    for (final Tag tag : tags) {
      if (tag.getDisamb()) {
        return tag;
      }
    }
    if (tags.size() > 0) {
      return tags.get(0);
    }
    return null;
  }

  public Set<Tag> getDisambTags() {
    final Set<Tag> tags = new HashSet<>();
    for (final Tag tag : this.tags) {
      if (tag.getDisamb()) {
        tags.add(tag);
      }
    }
    return tags;
  }

  public void packAtributes(final int size) {
    while (getNumAttributes() < size) {
      attributes.add(null);
    }
  }

  public void setAttributeValue(final int index, final String value) {
    if (index < attributes.size()) {
      attributes.set(index, value);
    } else if (index == attributes.size()) {
      attributes.add(value);
    }
  }

  public void setAttributeValue(final String attr, final String value) {
    final int index = attrIdx.getIndex(attr);
    setAttributeValue(index, value);
  }

  public void setNoSpaceAfter(final boolean noSpaceAfter) {
    this.noSpaceAfter = noSpaceAfter;
  }

  public String getAttributesAsString() {
    final StringBuilder sb = new StringBuilder();
    for (final String attr : attributes) {
      sb.append((sb.length() == 0 ? "" : ", ") + attr);
    }
    return sb.toString();
  }

  @Override
  public Token clone() {
    final Token cloned = new Token(attrIdx.clone());
    cloned.tags = new ArrayList<>(tags);
    cloned.attributes = new ArrayList<>(attributes);
    cloned.id = id;
    cloned.noSpaceAfter = noSpaceAfter;
    return cloned;
  }

  public void setAttributeIndex(final TokenAttributeIndex newAttrIdx) {
    final ArrayList<String> newAttributes = new ArrayList<>();
    for (final String feature : newAttrIdx.allAtributes()) {
      final String value = attrIdx.getIndex(feature) == -1 ? null : getAttributeValue(feature);
      newAttributes.add(value);
    }
    attrIdx = newAttrIdx;
    attributes = newAttributes;
  }

  public boolean isWrapped() {
    return getClass().isInstance(WrappedToken.class);
  }

  public TokenAttributeIndex getAttributeIndex() {
    return attrIdx;
  }

  public boolean hasBase(final String base, final boolean disambOnly) {
    for (final Tag tag : tags) {
      if (tag.getBase().equals(base) && (disambOnly == false || tag.getDisamb() == true)) {
        return true;
      }
    }
    return false;
  }

  public Token withNoSpaceAfter(final boolean noSpaceAfter) {
    this.noSpaceAfter = noSpaceAfter;
    return this;
  }

}
