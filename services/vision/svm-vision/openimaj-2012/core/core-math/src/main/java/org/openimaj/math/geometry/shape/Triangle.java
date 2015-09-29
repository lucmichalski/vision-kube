/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.math.geometry.shape;

import java.util.ArrayList;
import java.util.List;

import org.openimaj.math.geometry.line.Line2d;
import org.openimaj.math.geometry.point.Point2d;
import org.openimaj.math.geometry.point.Point2dImpl;

import Jama.Matrix;

/**
 * A triangle shape
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 */
public class Triangle implements Shape {
	/** The vertices of the triangle */
	public Point2d [] vertices = new Point2d[3];
	
	/**
	 * Construct a Triangle with the given vertices. 
	 * @param vertex1 first vertex
	 * @param vertex2 second vertex
	 * @param vertex3 third vertex
	 */
	public Triangle(Point2d vertex1, Point2d vertex2, Point2d vertex3) {
		this.vertices[0] = vertex1;
		this.vertices[1] = vertex2;
		this.vertices[2] = vertex3;
	}
	
	/**
	 * Construct a Triangle with the given vertices. 
	 * @param vertices the vertices
	 */
	public Triangle(Point2d[] vertices) {
		if (vertices.length != 3)
			throw new IllegalArgumentException("Triangles must have three vertices");
		this.vertices = vertices;
	}
	
	private final int getOrientation(float v1x, float v1y, float v2x, float v2y, float px, float py) {
		float ori = (v2x - v1x) * (py - v1y) - (px - v1x) * (v2y - v1y);
		
		if (ori == 0) return 0;
		return ori < 0 ? -1 : 1;
	}
	
	/* (non-Javadoc)
	 * 
	 * Note: often called in tight loops, so optimised
	 * 
	 * @see org.openimaj.math.geometry.shape.Shape#isInside(org.openimaj.math.geometry.point.Point2d)
	 */
	@Override
	public final boolean isInside(Point2d point) {
		final float v1x = vertices[0].getX();
		final float v1y = vertices[0].getY();
		final float v2x = vertices[1].getX();
		final float v2y = vertices[1].getY();
		final float v3x = vertices[2].getX();
		final float v3y = vertices[2].getY();
		final float px = point.getX();
		final float py = point.getY();
		
		if (px > v1x && px > v2x && px > v3x) return false;
		if (px < v1x && px < v2x && px < v3x) return false;
		if (py > v1y && py > v2y && py > v3y) return false;
		if (py < v1y && py < v2y && py < v3y) return false;
		
		int o1 = getOrientation(v1x, v1y, v2x, v2y, px, py);
		int o2 = getOrientation(v2x, v2y, v3x, v3y, px, py);
		int o3 = getOrientation(v3x, v3y, v1x, v1y, px, py);
		
		return (o1 == o2) && (o2 == o3);
	}
	
	@Override
	public Rectangle calculateRegularBoundingBox() {
		return new Rectangle((int) Math.round(minX()), (int) Math.round(minY()), (int) Math.round(getWidth()), (int) Math.round(getHeight()));
	}

	@Override
	public void translate(float x, float y) {
		for (Point2d v : vertices)
			v.translate(x, y);
	}

	@Override
	public void scale(float sc) {
		for (Point2d v : vertices) {
			v.setX(v.getX() * sc);
			v.setY(v.getY() * sc);
		}
	}

	@Override
	public void scale(Point2d centre, float sc) {
		translate(-centre.getX(), -centre.getY());
		scale(sc);
		translate(centre.getX(), centre.getY());
	}

	@Override
	public void scaleCOG(float sc) {
		Point2d centre = this.getCOG();
		translate(-centre.getX(), -centre.getY());
		scale(sc);
		translate(centre.getX(), centre.getY());
	}

	@Override
	public Point2d getCOG() {
		return new Point2dImpl(
				(vertices[0].getX() + vertices[1].getX() + vertices[2].getX())/3, 
				(vertices[0].getY() + vertices[1].getY() + vertices[2].getY())/3
			);
	}

	@Override
	public double calculateArea() {
		double xb = vertices[1].getX() - vertices[0].getX();
		double yb = vertices[1].getY() - vertices[0].getY();
		double xc = vertices[2].getX() - vertices[0].getX();
		double yc = vertices[2].getY() - vertices[0].getY();
		
		return 0.5 * Math.abs(xb*yc - xc*yb);
	}

	@Override
	public double minX() {
		return Math.min(vertices[0].getX(), Math.min(vertices[1].getX(), vertices[2].getX()));
	}

	@Override
	public double minY() {
		return Math.min(vertices[0].getY(), Math.min(vertices[1].getY(), vertices[2].getY()));
	}

	@Override
	public double maxX() {
		return Math.max(vertices[0].getX(), Math.max(vertices[1].getX(), vertices[2].getX()));
	}

	@Override
	public double maxY() {
		return Math.max(vertices[0].getY(), Math.max(vertices[1].getY(), vertices[2].getY()));
	}

	@Override
	public double getWidth() {
		return maxX() - minX();
	}

	@Override
	public double getHeight() {
		return maxY() - minY();
	}

	@Override
	public Triangle transform(Matrix transform) {
		Point2d[] newVertices = new Point2d[3];
		
		for (int i=0; i<3; i++) {
			Matrix p1 = new Matrix(3,1);
			p1.set(0, 0, vertices[i].getX());
			p1.set(1, 0, vertices[i].getY());
			p1.set(2, 0, 1);

			Matrix p2_est = transform.times(p1);

			Point2d out = new Point2dImpl((float)(p2_est.get(0,0) / p2_est.get(2, 0)), (float)(p2_est.get(1,0) / p2_est.get(2, 0)));
			
			newVertices[i] = out;
		}
		
		return new Triangle(newVertices);
	}

	@Override
	public Polygon asPolygon() {
		Polygon polygon = new Polygon(vertices);
		
		return polygon;
	}
	
	/**
	 * @return The first vertex.
	 */
	public Point2d firstVertex() {
		return vertices[0];
	}
	
	/**
	 * @return The second vertex.
	 */
	public Point2d secondVertex() {
		return vertices[1];
	}
	
	/**
	 * @return The third vertex.
	 */
	public Point2d thirdVertex() {
		return vertices[2];
	}
	
	/**
	 * @return The edges of the triangle.
	 */
	public List<Line2d> getEdges()
	{
		List<Line2d> edges = new ArrayList<Line2d>();
		edges.add( new Line2d( firstVertex(), secondVertex() ) );
		edges.add( new Line2d( secondVertex(), thirdVertex() ) );
		edges.add( new Line2d( thirdVertex(), firstVertex() ) );
		return edges;
	}
	
	/**
	 * Test whether this triangle shares a vertex with another triangle.
	 * @param other the other triangle.
	 * @return true if a vertex is shared; false otherwise.
	 */
	public boolean sharesVertex(Triangle other) {
		for (Point2d v1 : vertices) 
			for (Point2d v2 : other.vertices) 
				if (v1 == v2) return true;
		
		return false;
	}
	
	@Override
	public double intersectionArea(Shape that) {
		return intersectionArea(that,1);
	}

	@Override
	public double intersectionArea(Shape that, int nStepsPerDimention) {
		Rectangle overlapping = this.calculateRegularBoundingBox().overlapping(that.calculateRegularBoundingBox());
		if(overlapping==null)
			return 0;
		double intersection = 0;
		double step = Math.max(overlapping.width, overlapping.height)/(double)nStepsPerDimention;
		double nReads = 0;
		for(float x = overlapping.x; x < overlapping.x + overlapping.width; x+=step){
			for(float y = overlapping.y; y < overlapping.y + overlapping.height; y+=step){
				boolean insideThis = this.isInside(new Point2dImpl(x,y));
				boolean insideThat = that.isInside(new Point2dImpl(x,y));
				nReads++;
				if(insideThis && insideThat) {
					intersection++;
				}
			}
		}
		
		return (intersection/nReads) * (overlapping.width * overlapping.height);
	}
}
